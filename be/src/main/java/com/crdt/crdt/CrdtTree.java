package com.crdt.crdt;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.crdt.model.CrdtOperation;
import com.crdt.model.FileNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CRDT Tree implementation based on Kleppmann's "A highly-available move
 * operation for replicated trees"
 * This implementation ensures eventual consistency and prevents cycles in move
 * operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrdtTree {

    private String replicaId;
    private Map<UUID, TreeNode> nodes = new ConcurrentHashMap<>();
    private Map<String, Long> vectorClock = new ConcurrentHashMap<>();

    public CrdtTree(String replicaId) {
        this.replicaId = replicaId;
        this.vectorClock.put(replicaId, 0L);
    }

    /**
     * Add a new node to the tree
     */
    public TreeNode addNode(UUID nodeId, UUID parentId, String name, FileNode.FileType type, Long timestamp, String replicaId) {
        vectorClock.put(replicaId, timestamp); // Update vector clock

        TreeNode node = new TreeNode(
                nodeId, parentId, name, type, timestamp, replicaId, false);

        nodes.put(nodeId, node);

        return node;
    }

    /**
     * Update node properties
     */
    public TreeNode updateNode(UUID nodeId, String name, Long timestamp, String replicaId) {
        TreeNode node = nodes.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }

        vectorClock.put(replicaId, timestamp); // Update vector clock
        node.setName(name);
        node.setTimestamp(timestamp);
        node.setReplicaId(replicaId);

        return node;
    }

    /**
     * Mark node as deleted (tombstone)
     */
    public TreeNode deleteNode(UUID nodeId, Long timestamp, String replicaId) {
        TreeNode node = nodes.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }

        vectorClock.put(replicaId, timestamp); // Update vector clock
        node.setDeleted(true);
        node.setTimestamp(timestamp);
        node.setReplicaId(replicaId);

        return node;
    }

    /**
     * Move node to new parent using Kleppmann's algorithm
     * This prevents cycles and ensures eventual consistency
     */
    public TreeNode moveNode(UUID nodeId, UUID newParentId, Long timestamp, String replicaId) {
        TreeNode node = nodes.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }

        // Check if move would create a cycle
        if (wouldCreateCycle(nodeId, newParentId)) {
            throw new IllegalArgumentException("Move operation would create a cycle");
        }

        vectorClock.put(replicaId, timestamp); // Update vector clock

        node.setParentId(newParentId);
        node.setTimestamp(timestamp);
        node.setReplicaId(replicaId);

        return node;
    }

    /**
     * Check if moving node to newParent would create a cycle
     */
    private boolean wouldCreateCycle(UUID nodeId, UUID newParentId) {
        if (nodeId.equals(newParentId)) {
            return true;
        }

        TreeNode current = nodes.get(newParentId);
        while (current != null && current.getParentId() != null) {
            if (current.getParentId().equals(nodeId)) {
                return true;
            }
            current = nodes.get(current.getParentId());
        }

        return false;
    }

    /**
     * Merge operations from another replica
     */
    public void merge(CrdtOperation operation) {
        UUID nodeId = operation.getNodeId();
        TreeNode existingNode = nodes.get(nodeId);

        switch (operation.getType()) {
            case CREATE:
                if (existingNode == null || operation.getTimestamp() > existingNode.getTimestamp()) {
                    TreeNode newNode = new TreeNode(
                            nodeId, operation.getParentId(), operation.getNodeName(),
                            operation.getNodeType() != null ? operation.getNodeType() : FileNode.FileType.FOLDER, operation.getTimestamp(),
                            operation.getReplicaId(), false);
                    nodes.put(nodeId, newNode);
                }
                break;

            case UPDATE:
                if (existingNode != null && operation.getTimestamp() > existingNode.getTimestamp()) {
                    existingNode.setName(operation.getNodeName());
                    existingNode.setTimestamp(operation.getTimestamp());
                    existingNode.setReplicaId(operation.getReplicaId());
                }
                break;

            case DELETE:
                if (existingNode != null && operation.getTimestamp() > existingNode.getTimestamp()) {
                    existingNode.setDeleted(true);
                    existingNode.setTimestamp(operation.getTimestamp());
                    existingNode.setReplicaId(operation.getReplicaId());
                }
                break;

            case MOVE:
                if (existingNode != null && operation.getTimestamp() > existingNode.getTimestamp()) {
                    // Only apply move if it doesn't create a cycle
                    if (!wouldCreateCycle(nodeId, operation.getParentId())) {
                        existingNode.setParentId(operation.getParentId());
                        existingNode.setTimestamp(operation.getTimestamp());
                        existingNode.setReplicaId(operation.getReplicaId());
                    }
                }
                break;
        }

        // Update vector clock
        updateVectorClock(operation.getReplicaId(), operation.getTimestamp());
    }

    /**
     * Get tree structure as a hierarchical map
     */
    public Map<String, Object> getTreeStructure() {
        Map<String, Object> tree = new HashMap<>();
        List<TreeNode> rootNodes = nodes.values().stream()
                .filter(node -> !node.isDeleted())
                .filter(node -> node.getParentId() == null)
                .sorted(Comparator.comparing(TreeNode::getName))
                .collect(Collectors.toList());

        tree.put("nodes", buildNodeTree(rootNodes));
        tree.put("vectorClock", vectorClock);
        tree.put("replicaId", replicaId);

        return tree;
    }

    /**
     * Build hierarchical structure recursively
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

            List<TreeNode> children = this.nodes.values().stream()
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
     * Update vector clock with operation from another replica
     */
    private void updateVectorClock(String replicaId, Long timestamp) {
        vectorClock.put(replicaId, Math.max(
                vectorClock.getOrDefault(replicaId, 0L), timestamp));
    }

    /**
     * Get all nodes
     */
    public Collection<TreeNode> getAllNodes() {
        return nodes.values();
    }

    /**
     * Get node by ID
     */
    public TreeNode getNode(UUID nodeId) {
        return nodes.get(nodeId);
    }
}
