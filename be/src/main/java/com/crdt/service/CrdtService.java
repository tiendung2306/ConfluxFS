package com.crdt.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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

            // Get the latest timestamp from this replica's perspective
            Long lastTimestamp = crdtTree.getVectorClock().get(replicaId);
            if (lastTimestamp != null) {
                state.setLastOperationTimestamp(lastTimestamp);
            }

            state.setLastHeartbeat(java.time.LocalDateTime.now());
            state.setIsActive(false);

            replicaStateRepository.save(state);
            log.info("Successfully persisted replica state.");
        } catch (Exception e) {
            log.error("Failed to persist replica state on shutdown for replica ID: {}", replicaId, e);
        }
    }

    /**
     * Initialize CRDT Tree on startup
     * Loads ALL non-deleted files from database (from all replicas) to ensure CRDT
     * tree is in sync
     */
    public void initializeCrdtTree() {
        this.crdtTree = new CrdtTree(replicaId);
        log.info("Initializing CRDT Tree for replica: {}", replicaId);

        Optional<ReplicaState> stateOpt = replicaStateRepository.findByReplicaId(replicaId);
        List<CrdtOperation> operationsToReplay;

        if (stateOpt.isPresent()) {
            log.info("Found existing replica state. Rebuilding from last known state.");
            ReplicaState state = stateOpt.get();
            Map<String, Long> persistedClock = deserializeVectorClock(state.getVectorClock());
            crdtTree.setVectorClock(new java.util.concurrent.ConcurrentHashMap<>(persistedClock));

            // Find the oldest timestamp we have recorded from any *other* replica.
            // This is the safest point to start replaying from to ensure we don't miss
            // anything.
            long catchUpTimestamp = persistedClock.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(replicaId))
                    .mapToLong(Map.Entry::getValue)
                    .min()
                    .orElse(0L);

            log.info("Catch-up timestamp set to: {}. Fetching operations since then.", catchUpTimestamp);
            operationsToReplay = crdtOperationRepository.findByTimestampGreaterThanOrderByTimestamp(catchUpTimestamp);

        } else {
            log.info("No replica state found. Rebuilding from the beginning of the operation log.");
            operationsToReplay = crdtOperationRepository.findAll(org.springframework.data.domain.Sort.by("timestamp"));
        }

        log.info("Replaying {} operations to build in-memory CRDT tree...", operationsToReplay.size());
        for (CrdtOperation op : operationsToReplay) {
            // Apply operation to the in-memory tree only.
            // The database state is assumed to be the source of truth already.
            crdtTree.merge(op);
        }

        log.info("CRDT Tree initialized successfully with vector clock: {}", crdtTree.getVectorClock());
    }

    /**
     * Create a new folder
     */
    public FileNode createFolder(String name, UUID parentId, UUID userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        UUID nodeId = UUID.randomUUID();
        Long timestamp = hlcService.newTimestamp().asLong();

        // Apply to CRDT tree first to get the updated vector clock
        crdtTree.addNode(nodeId, parentId, name, FileNode.FileType.FOLDER, timestamp, replicaId);
        String currentVectorClock = serializeVectorClock(crdtTree.getVectorClock());

        // Create CRDT operation
        CrdtOperation operation = new CrdtOperation();
        operation.setType(CrdtOperation.OperationType.CREATE);
        operation.setNodeId(nodeId);
        operation.setParentId(parentId);
        operation.setNodeName(name);
        operation.setNodeType(FileNode.FileType.FOLDER);
        operation.setReplicaId(replicaId);
        operation.setTimestamp(timestamp);
        operation.setVectorClock(currentVectorClock);
        operation.setIsApplied(true); // Local operations are always applied

        // Create file node in database
        FileNode fileNode = new FileNode();
        fileNode.setId(nodeId);
        fileNode.setParentId(parentId);
        fileNode.setName(name);
        fileNode.setType(FileNode.FileType.FOLDER);
        fileNode.setReplicaId(replicaId);
        fileNode.setTimestamp(timestamp);
        fileNode.setVectorClock(currentVectorClock);
        fileNode.setIsDeleted(false);
        fileNode.setOwner(owner);

        FileNode savedNode = fileNodeRepository.save(fileNode);
        crdtOperationRepository.save(operation);

        // Broadcast operation to other replicas
        broadcastOperation(operation);

        // Emit realtime event
        publishEvent("file.created", buildNodeEventPayload(savedNode));

        return savedNode;
    }

    /**
     * Create a new file (metadata) and add to CRDT
     */
    public FileNode createFile(String name, UUID parentId, Long fileSize, String mimeType, String filePath,
            UUID userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        UUID nodeId = UUID.randomUUID();
        Long timestamp = hlcService.newTimestamp().asLong();

        // Apply to CRDT tree
        crdtTree.addNode(nodeId, parentId, name, FileNode.FileType.FILE, timestamp, replicaId);
        String currentVectorClock = serializeVectorClock(crdtTree.getVectorClock());

        CrdtOperation operation = new CrdtOperation();
        operation.setType(CrdtOperation.OperationType.CREATE);
        operation.setNodeId(nodeId);
        operation.setParentId(parentId);
        operation.setNodeName(name);
        operation.setNodeType(FileNode.FileType.FILE);
        operation.setReplicaId(replicaId);
        operation.setTimestamp(timestamp);
        operation.setVectorClock(currentVectorClock);
        operation.setIsApplied(true);

        // Create file node in database with metadata
        FileNode fileNode = new FileNode();
        fileNode.setId(nodeId);
        fileNode.setParentId(parentId);
        fileNode.setName(name);
        fileNode.setType(FileNode.FileType.FILE);
        fileNode.setFileSize(fileSize);
        fileNode.setMimeType(mimeType);
        fileNode.setFilePath(filePath);
        fileNode.setReplicaId(replicaId);
        fileNode.setTimestamp(timestamp);
        fileNode.setVectorClock(currentVectorClock);
        fileNode.setIsDeleted(false);
        fileNode.setOwner(owner);

        FileNode saved = fileNodeRepository.save(fileNode);
        crdtOperationRepository.save(operation);

        broadcastOperation(operation);

        // Emit realtime event
        publishEvent("file.created", buildNodeEventPayload(saved));

        return saved;
    }

    /**
     * Move a file/folder
     */
    public FileNode moveFile(UUID nodeId, UUID newParentId) {
        FileNode fileNode = fileNodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + nodeId));

        if (fileNode.getIsDeleted()) {
            throw new RuntimeException("Cannot move deleted file");
        }

        UUID oldParentId = fileNode.getParentId();
        Long timestamp = hlcService.newTimestamp().asLong();

        // Apply to CRDT tree
        crdtTree.moveNode(nodeId, newParentId, timestamp, replicaId);
        String currentVectorClock = serializeVectorClock(crdtTree.getVectorClock());

        // Create CRDT operation
        CrdtOperation operation = new CrdtOperation();
        operation.setType(CrdtOperation.OperationType.MOVE);
        operation.setNodeId(nodeId);
        operation.setParentId(newParentId);
        operation.setOldParentId(oldParentId);
        operation.setNodeName(fileNode.getName());
        operation.setNodeType(fileNode.getType());
        operation.setReplicaId(replicaId);
        operation.setTimestamp(timestamp);
        operation.setVectorClock(currentVectorClock);
        operation.setIsApplied(true);

        // Update file node in database
        fileNode.setParentId(newParentId);
        fileNode.setTimestamp(timestamp);
        fileNode.setVectorClock(currentVectorClock);

        FileNode savedNode = fileNodeRepository.save(fileNode);
        crdtOperationRepository.save(operation);

        // Broadcast operation to other replicas
        broadcastOperation(operation);

        publishEvent("file.moved", buildNodeEventPayload(savedNode));

        return savedNode;
    }

    /**
     * Delete a file/folder
     */
    public void deleteFile(UUID nodeId) {
        FileNode fileNode = fileNodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + nodeId));

        if (fileNode.getIsDeleted()) {
            throw new RuntimeException("File already deleted");
        }

        Long timestamp = hlcService.newTimestamp().asLong();

        // Apply to CRDT tree
        crdtTree.deleteNode(nodeId, timestamp, replicaId);
        String currentVectorClock = serializeVectorClock(crdtTree.getVectorClock());

        // Create CRDT operation
        CrdtOperation operation = new CrdtOperation();
        operation.setType(CrdtOperation.OperationType.DELETE);
        operation.setNodeId(nodeId);
        operation.setParentId(fileNode.getParentId());
        operation.setNodeName(fileNode.getName());
        operation.setNodeType(fileNode.getType());
        operation.setReplicaId(replicaId);
        operation.setTimestamp(timestamp);
        operation.setVectorClock(currentVectorClock);
        operation.setIsApplied(true);

        // Update file node in database
        fileNode.setIsDeleted(true);
        fileNode.setTimestamp(timestamp);
        fileNode.setVectorClock(currentVectorClock);

        fileNodeRepository.save(fileNode);
        crdtOperationRepository.save(operation);

        // Broadcast operation to other replicas
        broadcastOperation(operation);

        publishEvent("file.deleted", buildNodeEventPayload(fileNode));
    }

    /**
     * Update file/folder name
     */
    public FileNode updateFile(UUID nodeId, String newName) {
        FileNode fileNode = fileNodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + nodeId));

        if (fileNode.getIsDeleted()) {
            throw new RuntimeException("Cannot update deleted file");
        }

        Long timestamp = hlcService.newTimestamp().asLong();

        // Apply to CRDT tree
        crdtTree.updateNode(nodeId, newName, timestamp, replicaId);
        String currentVectorClock = serializeVectorClock(crdtTree.getVectorClock());

        // Create CRDT operation
        CrdtOperation operation = new CrdtOperation();
        operation.setType(CrdtOperation.OperationType.UPDATE);
        operation.setNodeId(nodeId);
        operation.setParentId(fileNode.getParentId());
        operation.setNodeName(newName);
        operation.setNodeType(fileNode.getType());
        operation.setReplicaId(replicaId);
        operation.setTimestamp(timestamp);
        operation.setVectorClock(currentVectorClock);
        operation.setIsApplied(true);

        // Update file node in database
        fileNode.setName(newName);
        fileNode.setTimestamp(timestamp);
        fileNode.setVectorClock(currentVectorClock);

        FileNode savedNode = fileNodeRepository.save(fileNode);
        crdtOperationRepository.save(operation);

        // Broadcast operation to other replicas
        broadcastOperation(operation);

        publishEvent("file.updated", buildNodeEventPayload(savedNode));
        return savedNode;
    }

    /**
     * Get tree structure
     */
    public Map<String, Object> getTreeStructure() {
        return crdtTree.getTreeStructure();
    }

    /**
     * Sync with other replicas by fetching operations we haven't seen yet,
     * based on our current vector clock.
     */
    public void syncWithReplicas() {
        publishEvent("sync.started", Map.of("replicaId", replicaId));
        log.debug("Starting sync for replica {}. Current vector clock: {}", replicaId, crdtTree.getVectorClock());

        // Discover all replicas that have ever made an operation
        List<String> allReplicaIds = crdtOperationRepository.findDistinctReplicaIds();
        int totalSynced = 0;

        for (String otherReplicaId : allReplicaIds) {
            if (otherReplicaId.equals(this.replicaId)) {
                continue; // Skip syncing with ourselves
            }

            // Get the last timestamp we've seen from this replica
            long lastSeenTimestamp = crdtTree.getVectorClock().getOrDefault(otherReplicaId, 0L);

            // Fetch all operations from that replica that are newer than what we've seen
            List<CrdtOperation> newOperations = crdtOperationRepository
                    .findByReplicaIdAndTimestampGreaterThanOrderByTimestamp(otherReplicaId, lastSeenTimestamp);

            if (!newOperations.isEmpty()) {
                log.info("Found {} new operations from replica {}", newOperations.size(), otherReplicaId);
                for (CrdtOperation operation : newOperations) {
                    try {
                        // Process each operation as if it came from an external source
                        processExternalOperation(operation);
                    } catch (Exception e) {
                        log.error("Error processing synced operation {} from replica {}",
                                operation.getId(), otherReplicaId, e);
                        // For now, we log and continue to not block other syncs.
                    }
                }
                totalSynced += newOperations.size();
            }
        }

        if (totalSynced > 0) {
            log.info("Synced {} total operations from other replicas", totalSynced);
            publishEvent("sync.completed", Map.of("replicaId", replicaId, "count", totalSynced));
        } else {
            log.debug("No new operations to sync from other replicas.");
        }
    }

    /**
     * Broadcast operation to other replicas via Redis
     */
    private void broadcastOperation(CrdtOperation operation) {
        try {
            redisTemplate.convertAndSend("crdt:operations", operation);
        } catch (Exception e) {
            log.warn("Failed to broadcast CRDT operation: {}", e.getMessage());
        }
    }

    /**
     * Apply operation to database
     */
    private void applyOperationToDatabase(CrdtOperation operation) {
        Optional<FileNode> existingNodeOpt = fileNodeRepository.findById(operation.getNodeId());

        // LWW check: Only apply if the incoming operation is newer than the existing
        // state
        if (existingNodeOpt.isPresent() && operation.getTimestamp() <= existingNodeOpt.get().getTimestamp()) {
            log.warn("Skipping older operation {} for node {}", operation.getType(), operation.getNodeId());
            return;
        }

        switch (operation.getType()) {
            case CREATE:
                if (existingNodeOpt.isEmpty()) {
                    FileNode newNode = new FileNode();
                    newNode.setId(operation.getNodeId());
                    newNode.setParentId(operation.getParentId());
                    newNode.setName(operation.getNodeName());
                    newNode.setType(
                            operation.getNodeType() != null ? operation.getNodeType() : FileNode.FileType.FOLDER);
                    newNode.setReplicaId(operation.getReplicaId());
                    newNode.setTimestamp(operation.getTimestamp());
                    newNode.setVectorClock(operation.getVectorClock());
                    newNode.setIsDeleted(false);
                    fileNodeRepository.save(newNode);
                }
                break;

            case UPDATE:
                existingNodeOpt.ifPresent(node -> {
                    node.setName(operation.getNodeName());
                    node.setTimestamp(operation.getTimestamp());
                    node.setReplicaId(operation.getReplicaId());
                    node.setVectorClock(operation.getVectorClock());
                    fileNodeRepository.save(node);
                });
                break;

            case DELETE:
                existingNodeOpt.ifPresent(node -> {
                    node.setIsDeleted(true);
                    node.setTimestamp(operation.getTimestamp());
                    node.setReplicaId(operation.getReplicaId());
                    node.setVectorClock(operation.getVectorClock());
                    fileNodeRepository.save(node);
                });
                break;

            case MOVE:
                existingNodeOpt.ifPresent(node -> {
                    node.setParentId(operation.getParentId());
                    node.setTimestamp(operation.getTimestamp());
                    node.setReplicaId(operation.getReplicaId());
                    node.setVectorClock(operation.getVectorClock());
                    fileNodeRepository.save(node);
                });
                break;
        }
    }

    /**
     * Serialize vector clock to JSON string using Jackson.
     */
    private String serializeVectorClock(Map<String, Long> vectorClock) {
        try {
            return objectMapper.writeValueAsString(vectorClock);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize vector clock: {}", vectorClock, e);
            return "{}"; // Return empty JSON object on failure
        }
    }

    private Map<String, Object> buildNodeEventPayload(FileNode node) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", node.getId());
        payload.put("parentId", node.getParentId());
        payload.put("name", node.getName());
        payload.put("type", node.getType().toString());
        payload.put("timestamp", node.getTimestamp());
        payload.put("replicaId", node.getReplicaId());
        return payload;
    }

    private void publishEvent(String type, Object data) {
        CrdtOperationEvent event = new CrdtOperationEvent(this, type, data);
        eventPublisher.publishEvent(event);
    }

    /**
     * Deserialize vector clock from JSON string using Jackson.
     */
    private Map<String, Long> deserializeVectorClock(String vectorClockJson) {
        if (vectorClockJson == null || vectorClockJson.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(vectorClockJson, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize vector clock from JSON: {}", vectorClockJson, e);
            return new HashMap<>(); // Return empty map on failure
        }
    }

    /**
     * Process external operation from other replicas
     */
    public void processExternalOperation(CrdtOperation operation) {
        // Ensure operation is not from the current replica to avoid loops
        if (this.replicaId.equals(operation.getReplicaId())) {
            return;
        }

        // CRITICAL: Update local clock with the timestamp from the remote operation
        // This ensures that any subsequent local operations will have a higher timestamp.
        hlcService.updateWithRemoteTimestamp(HybridLogicalClock.fromLong(operation.getTimestamp()));

        crdtTree.merge(operation);
        applyOperationToDatabase(operation);

        // Find the original operation in the DB to mark it as applied
        crdtOperationRepository.findById(operation.getId()).ifPresent(op -> {
            op.setIsApplied(true);
            crdtOperationRepository.save(op);
        });

        // Emit event based on operation
        String eventType = switch (operation.getType()) {
            case CREATE -> "file.created";
            case UPDATE -> "file.updated";
            case DELETE -> "file.deleted";
            case MOVE -> "file.moved";
        };
        publishEvent(eventType, Map.of(
                "id", operation.getNodeId(),
                "parentId", operation.getParentId(),
                "name", operation.getNodeName(),
                "replicaId", operation.getReplicaId(),
                "timestamp", operation.getTimestamp(),
                "type", operation.getNodeType() != null ? operation.getNodeType().toString() : null));
    }

    /**
     * Get operations since timestamp
     */
    public List<CrdtOperation> getOperationsSince(Long timestamp) {
        if (timestamp == null) {
            return crdtOperationRepository.findByReplicaIdOrderByTimestamp(replicaId);
        }
        return crdtOperationRepository.findByTimestampGreaterThanOrderByTimestamp(timestamp);
    }

    /**
     * Get replica ID
     */
    public String getReplicaId() {
        return replicaId;
    }

    /**
     * Get vector clock
     */
    public Map<String, Long> getVectorClock() {
        return crdtTree.getVectorClock();
    }

    /**
     * Copy a node (file or folder) to target parent. For folders, copy subtree.
     */
    public FileNode copyNode(UUID sourceId, UUID targetParentId, UUID userId) {
        FileNode source = fileNodeRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Source node not found with id: " + sourceId));

        if (source.getIsDeleted()) {
            throw new RuntimeException("Cannot copy deleted node");
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

        // Folder: create folder and recursively copy children
        FileNode newFolder = createFolder(source.getName(), targetParentId, userId);
        List<FileNode> children = fileNodeRepository.findByParentIdAndIsDeletedFalseOrderByName(sourceId);
        for (FileNode child : children) {
            copyNode(child.getId(), newFolder.getId(), userId);
        }
        return newFolder;
    }
}
