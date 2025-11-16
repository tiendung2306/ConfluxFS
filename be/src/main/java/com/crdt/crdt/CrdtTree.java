package com.crdt.crdt;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.crdt.model.CrdtOperation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

/**
 * CRDT Tree implementation based on Kleppmann's "A highly-available move
 * operation for replicated trees".
 * This implementation uses an undo-redo mechanism to ensure eventual
 * consistency and prevents cycles in move operations.
 */
@Data
public class CrdtTree {

    // A special UUID to represent the parent of deleted nodes (the "trash")
    public static final UUID TRASH_ROOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    // A special UUID for nodes that are at the root of the filesystem (no parent)
    public static final UUID VIRTUAL_ROOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private String replicaId;
    private Map<String, Long> vectorClock = new ConcurrentHashMap<>();

    // The canonical state of the tree
    private Map<UUID, TreeNode> nodeMap = new ConcurrentHashMap<>();
    // The operation log, sorted by timestamp descending. Essential for undo-redo.
    private List<CrdtOperation> operationLog = new LinkedList<>();

    /**
     * A simple, immutable state holder for passing the (log, tree) tuple, as
     * described in the paper.
     */
    @Getter
    @AllArgsConstructor
    private static class TreeState {
        private final List<CrdtOperation> log;
        private final Map<UUID, TreeNode> tree;
    }

    public CrdtTree(String replicaId) {
        this.replicaId = replicaId;
        this.vectorClock.put(replicaId, 0L);
    }

    /**
     * Applies a new operation to the tree, ensuring convergence by handling
     * out-of-order messages via an undo-redo mechanism.
     * This is the equivalent of the `apply_ops` fold in the paper.
     *
     * @param op The operation to apply.
     */
    public synchronized void applyOperation(CrdtOperation op) {
        TreeState initialState = new TreeState(this.operationLog, this.nodeMap);
        TreeState finalState = applyOpRecursive(op, initialState);

        // Atomically update the canonical state
        this.operationLog = finalState.getLog();
        this.nodeMap = finalState.getTree();

        // Update the vector clock for this replica
        updateVectorClock(op.getReplicaId(), op.getTimestamp());
    }

    /**
     * The recursive core of the algorithm, equivalent to `apply_op` in the paper.
     *
     * @param opToApply The new operation to integrate.
     * @param state     The current state (log and tree) to apply the operation to.
     * @return The new state after applying the operation.
     */
    private TreeState applyOpRecursive(CrdtOperation opToApply, TreeState state) {
        // Base case: If the log is empty, just perform the operation.
        if (state.getLog().isEmpty()) {
            return doOperation(opToApply, state.getTree());
        }

        List<CrdtOperation> currentLog = new LinkedList<>(state.getLog());
        CrdtOperation latestLogOp = currentLog.remove(0); // Get the head of the log.

        // Case 1: The new operation is concurrent or more recent than the latest in the log.
        // Apply it directly and add it to the head of the log.
        // We use >= to handle operations with identical timestamps, making the one being applied win.
        if (opToApply.getTimestamp() >= latestLogOp.getTimestamp()) {
            TreeState resultOfDo = doOperation(opToApply, state.getTree());
            List<CrdtOperation> newLog = new LinkedList<>(state.getLog());
            newLog.add(0, resultOfDo.getLog().get(0)); // Add the new log entry to the head.
            return new TreeState(newLog, resultOfDo.getTree());
        }

        // Case 2: The new operation is older than the latest in the log.
        // This is the out-of-order case that requires undo-recurse-redo.

        // 1. Undo the latest operation from the log.
        Map<UUID, TreeNode> undoneTree = undoOperation(latestLogOp, state.getTree());

        // 2. Recurse: Apply the new operation to the rest of the log (the tail).
        TreeState afterRecursion = applyOpRecursive(opToApply, new TreeState(currentLog, undoneTree));

        // 3. Redo the operation we previously undid, on the resulting state.
        // CRITICAL FIX: We must re-apply the move defined by `latestLogOp` but NOT regenerate
        // the log entry. The `latestLogOp` IS the historical record we must preserve.
        // We now separate "performing" the move from "logging" it.
        Map<UUID, TreeNode> redoneTree = performMove(latestLogOp, afterRecursion.getTree());

        // 4. Combine the results to form the final state.
        List<CrdtOperation> finalLog = new LinkedList<>(afterRecursion.getLog());
        finalLog.add(0, latestLogOp); // Add the ORIGINAL undone op to the head of the new log.

        return new TreeState(finalLog, redoneTree);
    }

    /**
     * Performs the actual move operation, checks for cycles, and generates a log
     * entry.
     * Equivalent to `do_op` in the paper.
     *
     * @param op   The operation to perform.
     * @param tree The current tree state.
     * @return A TreeState containing the new log entry and the updated tree.
     */
    private TreeState doOperation(CrdtOperation op, Map<UUID, TreeNode> tree) {
        TreeNode existingNode = tree.get(op.getNodeId());
        UUID oldParentId = (existingNode != null) ? existingNode.getParentId() : null;
        String oldName = (existingNode != null) ? existingNode.getName() : null;

        // Create the log operation, capturing the state *before* the change.
        CrdtOperation logOp = op.toBuilder()
                .oldParentId(oldParentId)
                .oldNodeName(oldName)
                .build();
        
        // Perform the actual move on the tree structure.
        Map<UUID, TreeNode> newTree = performMove(op, tree);

        // Return the new tree state along with the generated log entry.
        return new TreeState(List.of(logOp), newTree);
    }

    /**
     * Performs the state change of a move operation on a tree map, without creating a log entry.
     * This is the "pure" execution part of a move.
     *
     * @param op   The operation to perform.
     * @param tree The current tree state.
     * @return The updated tree map.
     */
    private Map<UUID, TreeNode> performMove(CrdtOperation op, Map<UUID, TreeNode> tree) {
        Map<UUID, TreeNode> newTree = new HashMap<>(tree);
        UUID childId = op.getNodeId();
        UUID newParentId = op.getParentId();

        // Ignore the operation if it would create a cycle.
        if (childId.equals(newParentId) || wouldCreateCycle(childId, newParentId, newTree)) {
            return tree; // Return original tree, no change.
        }

        TreeNode nodeToMove = newTree.get(childId);
        UUID oldParentId = (nodeToMove != null) ? nodeToMove.getParentId() : null;

        if (nodeToMove == null) { // This is a CREATE operation.
            nodeToMove = new TreeNode(childId, newParentId, op.getNodeName(), op.getNodeType(), op.getTimestamp(),
                    op.getReplicaId(), false);
        } else { // This is a MOVE, RENAME, or UNDELETE operation.
            // CLONE the node before modifying to prevent state leakage across recursive calls.
            nodeToMove = new TreeNode(nodeToMove);
            nodeToMove.setParentId(newParentId);
            nodeToMove.setName(op.getNodeName());
            nodeToMove.setTimestamp(op.getTimestamp());
            nodeToMove.setReplicaId(op.getReplicaId());
            // If moving out of trash, it's no longer deleted.
            if (TRASH_ROOT_ID.equals(oldParentId)) {
                nodeToMove.setDeleted(false);
            }
        }

        // If the new parent is the trash, mark as deleted.
        if (TRASH_ROOT_ID.equals(newParentId)) {
            nodeToMove.setDeleted(true);
        }

        newTree.put(childId, nodeToMove);
        return newTree;
    }

    /**
     * Reverts the effect of an operation on the tree.
     * Equivalent to `undo_op` in the paper.
     *
     * @param opToUndo The log entry of the operation to undo.
     * @param tree     The current tree state.
     * @return The tree state as it was before the operation.
     */
    private Map<UUID, TreeNode> undoOperation(CrdtOperation opToUndo, Map<UUID, TreeNode> tree) {
        Map<UUID, TreeNode> newTree = new HashMap<>(tree);
        UUID childId = opToUndo.getNodeId();
        UUID originalParentId = opToUndo.getOldParentId();
        String originalName = opToUndo.getOldNodeName();


        // If old parent is null, it was a creation, so we remove it.
        if (opToUndo.getOldParentId() == null) {
            newTree.remove(childId);
            return newTree;
        }

        TreeNode nodeToUndo = newTree.get(childId);
        if (nodeToUndo != null) {
            // CLONE the node before modifying to prevent state leakage.
            nodeToUndo = new TreeNode(nodeToUndo);
            nodeToUndo.setParentId(originalParentId);
            nodeToUndo.setName(originalName);
            // Restore the 'deleted' status based on the original parent.
            nodeToUndo.setDeleted(TRASH_ROOT_ID.equals(originalParentId));
            newTree.put(childId, nodeToUndo);
        }
        return newTree;
    }

    /**
     * Checks if moving a node to a new parent would create a cycle.
     *
     * @param nodeId      The ID of the node to move.
     * @param newParentId The ID of the potential new parent.
     * @param tree        The current tree structure.
     * @return True if a cycle would be created, false otherwise.
     */
    private boolean wouldCreateCycle(UUID nodeId, UUID newParentId, Map<UUID, TreeNode> tree) {
        if (newParentId == null || VIRTUAL_ROOT_ID.equals(newParentId) || TRASH_ROOT_ID.equals(newParentId)) {
            return false; // Can't create a cycle by moving to a root.
        }
        if (nodeId.equals(newParentId)) {
            return true; // Moving a node to itself is a cycle.
        }
        UUID currentId = newParentId;
        while (currentId != null) {
            if (currentId.equals(nodeId)) {
                return true; // Found the node to move in the ancestry of the new parent.
            }
            TreeNode currentNode = tree.get(currentId);
            currentId = (currentNode != null) ? currentNode.getParentId() : null;
        }
        return false;
    }

    /**
     * Get tree structure as a hierarchical map for API responses.
     */
    public Map<String, Object> getTreeStructure() {
        Map<String, Object> tree = new HashMap<>();
        // Filter out nodes in the trash and nodes that don't have a parent in the tree
        // (or have the virtual root as parent)
        List<TreeNode> rootNodes = this.nodeMap.values().stream()
                .filter(node -> !node.isDeleted())
                .filter(node -> node.getParentId() == null || VIRTUAL_ROOT_ID.equals(node.getParentId()))
                .sorted(Comparator.comparing(TreeNode::getName))
                .collect(Collectors.toList());

        tree.put("nodes", buildNodeTree(rootNodes));
        tree.put("vectorClock", vectorClock);
        tree.put("replicaId", replicaId);

        return tree;
    }

    /**
     * Build hierarchical structure recursively for the API.
     */
    private List<Map<String, Object>> buildNodeTree(List<TreeNode> nodes) {
        return nodes.stream().map(node -> {
            Map<String, Object> nodeMap = new HashMap<>();
            nodeMap.put("id", node.getId());
            nodeMap.put("parentId", node.getParentId());
            nodeMap.put("name", node.getName());
            nodeMap.put("type", node.getType());
            nodeMap.put("timestamp", node.getTimestamp());
            nodeMap.put("replicaId", node.getReplicaId());

            List<TreeNode> children = this.nodeMap.values().stream()
                    .filter(child -> !child.isDeleted())
                    .filter(child -> node.getId().equals(child.getParentId()))
                    .sorted(Comparator.comparing(TreeNode::getName))
                    .collect(Collectors.toList());

            if (!children.isEmpty()) {
                nodeMap.put("children", buildNodeTree(children));
            }

            return nodeMap;
        }).collect(Collectors.toList());
    }

    /**
     * Update vector clock with the timestamp from an operation.
     */
    private void updateVectorClock(String opReplicaId, Long timestamp) {
        vectorClock.put(opReplicaId, Math.max(
                vectorClock.getOrDefault(opReplicaId, 0L), timestamp));
    }

    /**
     * Get all nodes currently in the tree map.
     */
    public Collection<TreeNode> getAllNodes() {
        return nodeMap.values();
    }

    /**
     * Get a specific node by its ID from the canonical tree map.
     */
    public TreeNode getNode(UUID nodeId) {
        return nodeMap.get(nodeId);
    }
}
