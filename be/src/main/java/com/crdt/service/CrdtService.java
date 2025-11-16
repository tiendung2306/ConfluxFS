package com.crdt.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.crdt.crdt.CrdtTree;
import com.crdt.crdt.TreeNode;
import com.crdt.exception.ResourceNotFoundException;
import com.crdt.model.CrdtOperation;
import com.crdt.model.FileNode;
import com.crdt.model.ReplicaState;
import com.crdt.model.User;
import com.crdt.repository.CrdtOperationRepository;
import com.crdt.repository.FileNodeRepository;
import com.crdt.repository.ReplicaStateRepository;
import com.crdt.repository.UserRepository;
import com.crdt.util.HybridLogicalClock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CrdtService {

    private final FileNodeRepository fileNodeRepository;
    private final CrdtOperationRepository crdtOperationRepository;
    private final ReplicaStateRepository replicaStateRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final HLCService hlcService;

    @Value("${crdt.replica.id}")
    private String replicaId;

    @Value("${file.storage.path}")
    private String storagePath;

    private CrdtTree crdtTree;

    @PreDestroy
    public void persistStateOnShutdown() {
        log.info("Persisting replica state for replica ID: {}", replicaId);
        try {
            ReplicaState state = replicaStateRepository.findByReplicaId(replicaId)
                    .orElse(new ReplicaState());

            state.setReplicaId(replicaId);
            state.setVectorClock(serializeVectorClock(crdtTree.getVectorClock()));
            state.setLastOperationTimestamp(hlcService.getLatestHlc().asLong());
            state.setLastHeartbeat(java.time.LocalDateTime.now());
            state.setIsActive(false);

            replicaStateRepository.save(state);
            log.info("Successfully persisted replica state.");
        } catch (Exception e) {
            log.error("Failed to persist replica state on shutdown for replica ID: {}", replicaId, e);
        }
    }

    /**
     * Initializes the CRDT Tree on startup by replaying all historical operations
     * from the database in timestamp order. This ensures the in-memory tree state
     * is correct and converged.
     */
    public void initializeCrdtTree() {
        this.crdtTree = new CrdtTree(replicaId);
        log.info("Initializing CRDT Tree for replica: {}", replicaId);

        // Fetch all operations from the database, strictly ordered by timestamp.
        List<CrdtOperation> operationsToReplay = crdtOperationRepository.findAll(Sort.by("timestamp"));

        log.info("Replaying {} operations to build in-memory CRDT tree...", operationsToReplay.size());
        for (CrdtOperation op : operationsToReplay) {
            // Update HLC with each operation's timestamp to ensure the clock is up-to-date.
            hlcService.updateWithRemoteTimestamp(HybridLogicalClock.fromLong(op.getTimestamp()));
            // Apply the operation to the in-memory tree.
            // Since operations are sorted, this will not trigger the undo-redo path,
            // making initialization efficient.
            crdtTree.applyOperation(op);
        }

        log.info("CRDT Tree initialized successfully. Final vector clock: {}", crdtTree.getVectorClock());
    }

    /**
     * Creates a new folder. This is modeled as a MOVE operation where the node is
     * "moved" from a null parent into the tree.
     */
    public CrdtServiceResult createFolder(String name, UUID parentId, UUID userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        UUID nodeId = UUID.randomUUID();
        long timestamp = hlcService.newTimestamp().asLong();
        UUID effectiveParentId = (parentId != null) ? parentId : CrdtTree.VIRTUAL_ROOT_ID;

        CrdtOperation operation = CrdtOperation.builder()
                .id(UUID.randomUUID())
                .nodeId(nodeId)
                .parentId(effectiveParentId)
                .oldParentId(null) // `null` oldParentId signifies creation
                .nodeName(name)
                .nodeType(FileNode.FileType.FOLDER)
                .replicaId(replicaId)
                .timestamp(timestamp)
                .isApplied(true)
                .build();

        // Apply and persist
        return applyAndPersist(operation, owner);
    }

    /**
     * Creates a new file metadata entry. Also modeled as a MOVE from a null parent.
     */
    public CrdtServiceResult createFile(String name, UUID parentId, Long fileSize, String mimeType, String filePath,
            UUID userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        UUID nodeId = UUID.randomUUID();
        long timestamp = hlcService.newTimestamp().asLong();
        UUID effectiveParentId = (parentId != null) ? parentId : CrdtTree.VIRTUAL_ROOT_ID;

        CrdtOperation operation = CrdtOperation.builder()
                .id(UUID.randomUUID())
                .nodeId(nodeId)
                .parentId(effectiveParentId)
                .oldParentId(null) // `null` oldParentId signifies creation
                .nodeName(name)
                .nodeType(FileNode.FileType.FILE)
                .replicaId(replicaId)
                .timestamp(timestamp)
                .isApplied(true)
                .build();

        CrdtServiceResult result = applyAndPersist(operation, owner);
        FileNode newNode = result.getFileNode();

        // Update physical file attributes
        newNode.setFileSize(fileSize);
        newNode.setMimeType(mimeType);
        newNode.setFilePath(filePath);
        FileNode savedNode = fileNodeRepository.save(newNode);
        return new CrdtServiceResult(savedNode, result.getOperation());
    }

    /**
     * Moves a file or folder. This is the canonical MOVE operation.
     */
    public CrdtServiceResult moveFile(UUID nodeId, UUID newParentId) {
        FileNode fileNode = fileNodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + nodeId));

        long timestamp = hlcService.newTimestamp().asLong();
        UUID effectiveParentId = (newParentId != null) ? newParentId : CrdtTree.VIRTUAL_ROOT_ID;

        CrdtOperation operation = CrdtOperation.builder()
                .id(UUID.randomUUID())
                .nodeId(nodeId)
                .parentId(effectiveParentId)
                .oldParentId(fileNode.getParentId())
                .nodeName(fileNode.getName()) // Name doesn't change in a pure move
                .oldNodeName(fileNode.getName())
                .nodeType(fileNode.getType())
                .replicaId(replicaId)
                .timestamp(timestamp)
                .isApplied(true)
                .build();

        return applyAndPersist(operation, fileNode.getOwner());
    }

    /**
     * Deletes a file or folder. This is modeled as a MOVE to a special "trash"
     * parent node.
     */
    public CrdtServiceResult deleteFile(UUID nodeId) {
        FileNode fileNode = fileNodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + nodeId));

        if (fileNode.getIsDeleted()) {
            throw new IllegalStateException("File is already deleted.");
        }

        long timestamp = hlcService.newTimestamp().asLong();

        CrdtOperation operation = CrdtOperation.builder()
                .id(UUID.randomUUID())
                .nodeId(nodeId)
                .parentId(CrdtTree.TRASH_ROOT_ID) // Move to trash
                .oldParentId(fileNode.getParentId())
                .nodeName(fileNode.getName())
                .oldNodeName(fileNode.getName())
                .nodeType(fileNode.getType())
                .replicaId(replicaId)
                .timestamp(timestamp)
                .isApplied(true)
                .build();

        return applyAndPersist(operation, fileNode.getOwner());
    }

    /**
     * Updates a file/folder name. This is modeled as a MOVE to the same parent but
     * with new metadata (the name).
     */
    public CrdtServiceResult updateFile(UUID nodeId, String newName) {
        FileNode fileNode = fileNodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + nodeId));

        if (fileNode.getIsDeleted()) {
            throw new IllegalStateException("Cannot update a deleted file.");
        }

        long timestamp = hlcService.newTimestamp().asLong();

        CrdtOperation operation = CrdtOperation.builder()
                .id(UUID.randomUUID())
                .nodeId(nodeId)
                .parentId(fileNode.getParentId()) // Parent does not change
                .oldParentId(fileNode.getParentId())
                .nodeName(newName)
                .oldNodeName(fileNode.getName()) // Capture old name for undo
                .nodeType(fileNode.getType())
                .replicaId(replicaId)
                .timestamp(timestamp)
                .isApplied(true)
                .build();

        return applyAndPersist(operation, fileNode.getOwner());
    }

    /**
     * Processes an operation from an external source (another replica via Redis or
     * sync).
     */
    public void processExternalOperation(CrdtOperation operation) {
        if (this.replicaId.equals(operation.getReplicaId())) {
            return; // Avoid processing our own broadcasted operations.
        }

        // CRITICAL: Update local HLC with the timestamp from the remote operation.
        hlcService.updateWithRemoteTimestamp(HybridLogicalClock.fromLong(operation.getTimestamp()));

        // Apply the operation to the in-memory CRDT tree.
        crdtTree.applyOperation(operation);

        // Persist the converged state of the affected node to the database.
        persistNodeState(operation.getNodeId(), null);

        // Mark the operation as applied in the database if it exists.
        crdtOperationRepository.findById(operation.getId()).ifPresent(op -> {
            if (op.getIsApplied() == null || !op.getIsApplied()) {
                op.setIsApplied(true);
                crdtOperationRepository.save(op);
            }
        });

        // Publish event for real-time UI updates.
        publishEvent("file.externally_modified", buildNodeEventPayload(crdtTree.getNode(operation.getNodeId())));
    }

    /**
     * Centralized method to apply an operation to the CRDT tree and persist the
     * results.
     *
     * @param operation The operation to apply.
     * @param owner     The owner of the node (for creation).
     * @return The persisted FileNode with the converged state and the operation.
     */
    private CrdtServiceResult applyAndPersist(CrdtOperation operation, User owner) {
        // 1. Apply to the in-memory CRDT tree. This is the source of truth.
        crdtTree.applyOperation(operation);

        // 2. Persist the operation itself to the log.
        operation.setVectorClock(serializeVectorClock(crdtTree.getVectorClock()));
        CrdtOperation savedOperation = crdtOperationRepository.save(operation);

        // 3. Persist the converged state of the node to the database.
        FileNode persistedNode = persistNodeState(operation.getNodeId(), owner);

        // 4. Broadcast to other replicas.
        broadcastOperation(savedOperation);

        // 5. Publish event for local UI.
        publishEvent("file.locally_modified", buildNodeEventPayload(persistedNode));

        return new CrdtServiceResult(persistedNode, savedOperation);
    }

    /**
     * Persists the converged state of a node from the in-memory tree to the
     * database.
     *
     * @param nodeId The ID of the node to persist.
     * @param owner  The user who owns the file (used only for creation).
     * @return The saved FileNode entity.
     */
    private FileNode persistNodeState(UUID nodeId, User owner) {
        // Get the converged, authoritative state from the CRDT tree.
        TreeNode treeNode = crdtTree.getNode(nodeId);

        if (treeNode == null) {
            // This can happen if the node was created and then removed in the same
            // undo/redo cycle.
            // We can treat this as a deletion.
            fileNodeRepository.findById(nodeId).ifPresent(fileNode -> {
                if (!fileNode.getIsDeleted()) {
                    fileNode.setIsDeleted(true);
                    fileNodeRepository.save(fileNode);
                }
            });
            return null;
        }

        FileNode fileNode = fileNodeRepository.findById(nodeId).orElse(new FileNode());

        // If it's a new node, set ID and owner.
        if (fileNode.getId() == null) {
            fileNode.setId(treeNode.getId());
            fileNode.setOwner(owner);
        }

        // Update attributes from the converged TreeNode state.
        fileNode.setName(treeNode.getName());
        fileNode.setParentId(treeNode.getParentId());
        fileNode.setType(treeNode.getType());
        fileNode.setIsDeleted(treeNode.isDeleted());
        fileNode.setTimestamp(treeNode.getTimestamp());
        fileNode.setReplicaId(treeNode.getReplicaId());
        fileNode.setVectorClock(serializeVectorClock(crdtTree.getVectorClock()));

        return fileNodeRepository.save(fileNode);
    }

    /**
     * Syncs with other replicas by fetching operations newer than our last known
     * timestamp for each replica.
     */
    public void syncWithReplicas() {
        publishEvent("sync.started", Map.of("replicaId", replicaId));
        log.debug("Starting sync for replica {}. Current vector clock: {}", replicaId, crdtTree.getVectorClock());

        List<String> allReplicaIds = crdtOperationRepository.findDistinctReplicaIds();
        int totalSynced = 0;

        for (String otherReplicaId : allReplicaIds) {
            if (otherReplicaId.equals(this.replicaId)) {
                continue;
            }

            long lastSeenTimestamp = crdtTree.getVectorClock().getOrDefault(otherReplicaId, 0L);
            List<CrdtOperation> newOperations = crdtOperationRepository
                    .findByReplicaIdAndTimestampGreaterThanOrderByTimestamp(otherReplicaId, lastSeenTimestamp);

            if (!newOperations.isEmpty()) {
                log.info("Found {} new operations from replica {}", newOperations.size(), otherReplicaId);
                newOperations.forEach(this::processExternalOperation);
                totalSynced += newOperations.size();
            }
        }

        if (totalSynced > 0) {
            log.info("Synced {} total operations from other replicas.", totalSynced);
            publishEvent("sync.completed", Map.of("replicaId", replicaId, "count", totalSynced));
        } else {
            log.debug("No new operations to sync from other replicas.");
        }
    }

    private void broadcastOperation(CrdtOperation operation) {
        try {
            redisTemplate.convertAndSend("crdt:operations", operation);
        } catch (Exception e) {
            log.warn("Failed to broadcast CRDT operation: {}", e.getMessage());
        }
    }

    private String serializeVectorClock(Map<String, Long> vectorClock) {
        try {
            return objectMapper.writeValueAsString(vectorClock);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize vector clock: {}", vectorClock, e);
            return "{}";
        }
    }

    private Map<String, Long> deserializeVectorClock(String vectorClockJson) {
        if (vectorClockJson == null || vectorClockJson.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(vectorClockJson, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize vector clock from JSON: {}", vectorClockJson, e);
            return new HashMap<>();
        }
    }

    private Map<String, Object> buildNodeEventPayload(TreeNode node) {
        if (node == null)
            return Map.of();
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", node.getId());
        payload.put("parentId", node.getParentId());
        payload.put("name", node.getName());
        payload.put("type", node.getType().toString());
        payload.put("timestamp", node.getTimestamp());
        payload.put("replicaId", node.getReplicaId());
        payload.put("isDeleted", node.isDeleted());
        return payload;
    }

    private Map<String, Object> buildNodeEventPayload(FileNode node) {
        if (node == null)
            return Map.of();
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", node.getId());
        payload.put("parentId", node.getParentId());
        payload.put("name", node.getName());
        payload.put("type", node.getType().toString());
        payload.put("timestamp", node.getTimestamp());
        payload.put("replicaId", node.getReplicaId());
        payload.put("isDeleted", node.getIsDeleted());
        return payload;
    }

    private void publishEvent(String type, Object data) {
        eventPublisher.publishEvent(new CrdtOperationEvent(this, type, data));
    }

    // --- Read-only methods ---

    public Map<String, Object> getTreeStructure() {
        return crdtTree.getTreeStructure();
    }

    public List<CrdtOperation> getOperationsSince(Long timestamp) {
        if (timestamp == null) {
            return crdtOperationRepository.findAll(Sort.by("timestamp"));
        }
        return crdtOperationRepository.findByTimestampGreaterThanOrderByTimestamp(timestamp);
    }

    public String getReplicaId() {
        return replicaId;
    }

    public Map<String, Long> getVectorClock() {
        return crdtTree.getVectorClock();
    }

    public TreeNode getNode(UUID nodeId) {
        return crdtTree.getNode(nodeId);
    }

    public java.util.Collection<TreeNode> getNodes() {
        return crdtTree.getAllNodes();
    }

    public CrdtServiceResult copyNode(UUID sourceId, UUID targetParentId, UUID userId) {
        FileNode source = fileNodeRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Source node not found with id: " + sourceId));

        if (source.getIsDeleted()) {
            throw new IllegalStateException("Cannot copy a deleted node.");
        }

        if (source.getType() == FileNode.FileType.FILE) {
            try {
                FileService.DuplicateResult dup = FileService.duplicateFileStatic(source.getFilePath(),
                        source.getName(), storagePath);
                return createFile(source.getName(), targetParentId, dup.size, source.getMimeType(), dup.filePath,
                        userId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to duplicate file: " + e.getMessage());
            }
        }

        // Folder: create the new folder and recursively copy its children.
        CrdtServiceResult newFolderResult = createFolder(source.getName(), targetParentId, userId);
        List<FileNode> children = fileNodeRepository.findByParentIdAndIsDeletedFalseOrderByName(sourceId);
        for (FileNode child : children) {
            copyNode(child.getId(), newFolderResult.getFileNode().getId(), userId);
        }
        return newFolderResult;
    }
}
