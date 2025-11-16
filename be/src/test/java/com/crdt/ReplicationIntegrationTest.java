package com.crdt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.crdt.model.CrdtOperation;
import com.crdt.model.FileNode;
import com.crdt.repository.CrdtOperationRepository;
import com.crdt.repository.FileNodeRepository;
import com.crdt.repository.UserRepository;
import com.crdt.service.CrdtService;
import com.crdt.service.CrdtServiceResult;
import com.crdt.service.HLCService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@Testcontainers
@ContextConfiguration(initializers = { ReplicationIntegrationTest.Initializer.class })
class ReplicationIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            TestPropertyValues.of(
                    "spring.datasource.url=" + postgres.getJdbcUrl(),
                    "spring.datasource.username=" + postgres.getUsername(),
                    "spring.datasource.password=" + postgres.getPassword(),
                    "spring.data.redis.host=" + redis.getHost(),
                    "spring.data.redis.port=" + redis.getFirstMappedPort())
                    .applyTo(configurableApplicationContext.getEnvironment());
        }
    }

    @Autowired
    private FileNodeRepository fileNodeRepository;
    @Autowired
    private CrdtOperationRepository crdtOperationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private ObjectMapper objectMapper;

    private CrdtService replica1Service;
    private CrdtService replica2Service;
    private CrdtService replica3Service;

    private com.crdt.model.User testUser;

    @BeforeEach
    void setupTest() {
        // Clean database before each test to ensure isolation
        crdtOperationRepository.deleteAll();
        fileNodeRepository.deleteAll();
        userRepository.deleteAll(); // Also clean users to be safe

        // Create user for the test
        testUser = userRepository.save(new com.crdt.model.User(null, "testuser", "test@test.com", "password", "Test",
                "User", true, null, null));

        // Each service gets its own HLC and CRDT Tree, simulating separate replicas
        replica1Service = createReplicaService("replica-1");
        replica2Service = createReplicaService("replica-2");
        replica3Service = createReplicaService("replica-3");
    }

    private CrdtService createReplicaService(String replicaId) {
        CrdtService service = new CrdtService(
                fileNodeRepository,
                crdtOperationRepository,
                null, // replicaStateRepository not needed for this test logic
                userRepository,
                redisTemplate,
                eventPublisher,
                objectMapper,
                new HLCService() // Each replica has its own clock
        );
        // Manually set replicaId and initialize an empty tree
        org.springframework.test.util.ReflectionTestUtils.setField(service, "replicaId", replicaId);
        service.initializeCrdtTree();
        return service;
    }

    // Helper to create a folder and ensure it's persisted for subsequent operations
    private FileNode createFolder(CrdtService service, String name, UUID parentId) {
        CrdtServiceResult result = service.createFolder(name, parentId, testUser.getId());
        // DO NOT SYNC HERE. Syncing should be done manually in tests only when needed.
        return result.getFileNode();
    }

    // Simulates broadcasting and processing all pending operations between replicas
    private void syncAllReplicas() {
        List<CrdtOperation> allOps = crdtOperationRepository.findAll();
        for (CrdtOperation op : allOps) {
            replica1Service.processExternalOperation(op);
            replica2Service.processExternalOperation(op);
            replica3Service.processExternalOperation(op);
        }
    }

    private void assertNodeParent(UUID nodeId, UUID expectedParentId) {
        // Assert against each replica's in-memory state
        assertThat(replica1Service.getNode(nodeId).getParentId()).isEqualTo(expectedParentId);
        assertThat(replica2Service.getNode(nodeId).getParentId()).isEqualTo(expectedParentId);
        assertThat(replica3Service.getNode(nodeId).getParentId()).isEqualTo(expectedParentId);

        // Assert against the database state
        FileNode nodeFromDb = fileNodeRepository.findById(nodeId).orElseThrow();
        assertThat(nodeFromDb.getParentId()).isEqualTo(expectedParentId);
    }

    private void assertNodeName(UUID nodeId, String expectedName) {
        assertThat(replica1Service.getNode(nodeId).getName()).isEqualTo(expectedName);
        assertThat(replica2Service.getNode(nodeId).getName()).isEqualTo(expectedName);
        assertThat(replica3Service.getNode(nodeId).getName()).isEqualTo(expectedName);
        assertThat(fileNodeRepository.findById(nodeId).get().getName()).isEqualTo(expectedName);
    }

    @Nested
    @DisplayName("A. Kịch bản Cơ bản (Basic Scenarios)")
    class BasicScenarios {

        @Test
        @DisplayName("1. Tạo thư mục gốc: Phải tồn tại trên tất cả các replica")
        void whenFolderIsCreated_thenExistsOnAllReplicas() {
            // When: A folder is created on one replica
            FileNode folderA = createFolder(replica1Service, "FolderA", null);
            syncAllReplicas();

            // Then: The folder should exist in the database and on other replicas
            assertThat(fileNodeRepository.existsById(folderA.getId())).isTrue();
            assertThat(replica2Service.getNode(folderA.getId())).isNotNull();
            assertThat(replica3Service.getNode(folderA.getId())).isNotNull();
            assertNodeName(folderA.getId(), "FolderA");
        }

        @Test
        @DisplayName("2. Tạo thư mục con: Phải có đúng parentId")
        void whenSubFolderIsCreated_thenHasCorrectParent() {
            // Given: A parent folder
            FileNode folderA = createFolder(replica1Service, "FolderA", null);
            syncAllReplicas();

            // When: A sub-folder is created inside the parent
            FileNode folderB = createFolder(replica1Service, "FolderB", folderA.getId());
            syncAllReplicas();

            // Then: The sub-folder's parentId should be correct on all replicas
            assertNodeParent(folderB.getId(), folderA.getId());
        }

        @Test
        @DisplayName("3. Đổi tên thư mục: Tên mới phải được cập nhật ở mọi nơi")
        void whenFolderIsRenamed_thenNameIsUpdatedOnAllReplicas() {
            // Given: A folder
            FileNode folderA = createFolder(replica1Service, "OldName", null);

            // When: The folder is renamed
            replica1Service.updateFile(folderA.getId(), "NewName");
            syncAllReplicas();

            // Then: The new name should be reflected on all replicas
            assertNodeName(folderA.getId(), "NewName");
        }

        @Test
        @DisplayName("4. Xóa thư mục: Phải được đánh dấu đã xóa")
        void whenFolderIsDeleted_thenIsMarkedAsDeleted() {
            // Given: A folder
            FileNode folderA = createFolder(replica1Service, "FolderA", null);

            // When: The folder is deleted
            replica1Service.deleteFile(folderA.getId());
            syncAllReplicas();

            // Then: The folder should be marked as deleted in the database and in-memory
            // trees
            assertThat(fileNodeRepository.findById(folderA.getId()).get().getIsDeleted()).isTrue();
            assertThat(replica1Service.getNode(folderA.getId()).isDeleted()).isTrue();
            assertThat(replica2Service.getNode(folderA.getId()).isDeleted()).isTrue();
        }

        @Test
        @DisplayName("5. Tạo tên trùng lặp: Hệ thống phải cho phép tạo (CRDT-specific)")
        void whenDuplicateNameIsCreated_thenBothNodesExist() {
            // Given: A parent folder
            FileNode folderA = createFolder(replica1Service, "FolderA", null);

            // When: Two folders with the same name are created in the same parent
            FileNode child1 = createFolder(replica1Service, "Duplicate", folderA.getId());
            FileNode child2 = createFolder(replica2Service, "Duplicate", folderA.getId());
            syncAllReplicas();

            // Then: Both nodes should exist with different IDs but the same parent and name
            assertThat(child1.getId()).isNotEqualTo(child2.getId());
            assertNodeParent(child1.getId(), folderA.getId());
            assertNodeParent(child2.getId(), folderA.getId());
            assertNodeName(child1.getId(), "Duplicate");
            assertNodeName(child2.getId(), "Duplicate");
        }
    }

    @Nested
    @DisplayName("B. Kịch bản Xung đột Đồng thời (Concurrent Conflicts)")
    class ConcurrentConflictTests {

        @Test
        @DisplayName("6. Di chuyển cùng một node: Phải hội tụ về một cha duy nhất dựa trên timestamp cao hơn")
        void whenConcurrentMovesOfSameNode_thenConvergesToHighestTimestampParent() {
            // Given: Three folders, A, B, and a node X to be moved
            FileNode folderA = createFolder(replica1Service, "FolderA", null);
            FileNode folderB = createFolder(replica1Service, "FolderB", null);
            FileNode nodeX = createFolder(replica1Service, "NodeX", null);
            syncAllReplicas();

            // When: Replica 1 moves X to A, and Replica 2 concurrently moves X to B
            CrdtOperation op1 = replica1Service.moveFile(nodeX.getId(), folderA.getId()).getOperation();
            CrdtOperation op2 = replica2Service.moveFile(nodeX.getId(), folderB.getId()).getOperation();

            // And: Replicas sync by processing each other's operations
            replica1Service.processExternalOperation(op2);
            replica2Service.processExternalOperation(op1);
            replica3Service.processExternalOperation(op1);
            replica3Service.processExternalOperation(op2);

            // Then: The final parent of NodeX should be the one from the operation with the
            // higher timestamp
            UUID winningParentId = op1.getTimestamp() > op2.getTimestamp() ? folderA.getId() : folderB.getId();
            assertNodeParent(nodeX.getId(), winningParentId);
        }

        @Test
        @DisplayName("7. Đổi tên cùng một node: Phải hội tụ về tên có timestamp cao hơn")
        void whenConcurrentRenamesOfSameNode_thenConvergesToHighestTimestampName() {
            // Given: A node X
            FileNode nodeX = createFolder(replica1Service, "OriginalName", null);
            syncAllReplicas();

            // When: Replica 1 renames X to "Name1", and Replica 2 concurrently renames it
            // to "Name2"
            CrdtOperation op1 = replica1Service.updateFile(nodeX.getId(), "Name1").getOperation();
            CrdtOperation op2 = replica2Service.updateFile(nodeX.getId(), "Name2").getOperation();

            // And: Replicas sync
            replica1Service.processExternalOperation(op2);
            replica2Service.processExternalOperation(op1);
            replica3Service.processExternalOperation(op1);
            replica3Service.processExternalOperation(op2);

            // Then: The final name should be the one from the operation with the higher
            // timestamp
            String winningName = op1.getTimestamp() > op2.getTimestamp() ? "Name1" : "Name2";
            assertNodeName(nodeX.getId(), winningName);
        }

        @Test
        @DisplayName("8. Vừa di chuyển vừa xóa: Phải hội tụ về một trạng thái duy nhất (di chuyển hoặc xóa)")
        void whenConcurrentMoveAndDelete_thenConvergesToHighestTimestampState() {
            // Given: A node X and a folder A
            FileNode nodeX = createFolder(replica1Service, "X", null);
            FileNode folderA = createFolder(replica1Service, "A", null);
            syncAllReplicas();

            // When: Replica 1 moves X to A, Replica 2 deletes X
            CrdtOperation op1_move = replica1Service.moveFile(nodeX.getId(), folderA.getId()).getOperation();
            CrdtOperation op2_delete = replica2Service.deleteFile(nodeX.getId()).getOperation();

            // And: Sync
            replica1Service.processExternalOperation(op2_delete);
            replica2Service.processExternalOperation(op1_move);
            replica3Service.processExternalOperation(op1_move);
            replica3Service.processExternalOperation(op2_delete);

            // Then: The outcome depends on the highest timestamp
            boolean deleteWins = op2_delete.getTimestamp() > op1_move.getTimestamp();

            if (deleteWins) {
                assertNodeParent(nodeX.getId(), com.crdt.crdt.CrdtTree.TRASH_ROOT_ID);
                assertThat(fileNodeRepository.findById(nodeX.getId()).get().getIsDeleted()).isTrue();
            } else {
                assertNodeParent(nodeX.getId(), folderA.getId());
                assertThat(fileNodeRepository.findById(nodeX.getId()).get().getIsDeleted()).isFalse();
            }
        }

        @Test
        @DisplayName("9. Vừa đổi tên vừa xóa: Phải hội tụ về một trạng thái duy nhất (đổi tên hoặc xóa)")
        void whenConcurrentRenameAndDelete_thenConvergesToHighestTimestampState() {
            // Given: A node X
            FileNode nodeX = createFolder(replica1Service, "X", null);
            syncAllReplicas();

            // When: Replica 1 renames X, Replica 2 deletes X
            CrdtOperation op1_rename = replica1Service.updateFile(nodeX.getId(), "NewName").getOperation();
            CrdtOperation op2_delete = replica2Service.deleteFile(nodeX.getId()).getOperation();

            // And: Sync
            replica1Service.processExternalOperation(op2_delete);
            replica2Service.processExternalOperation(op1_rename);
            replica3Service.processExternalOperation(op1_rename);
            replica3Service.processExternalOperation(op2_delete);

            // Then: The outcome depends on the highest timestamp
            boolean deleteWins = op2_delete.getTimestamp() > op1_rename.getTimestamp();

            if (deleteWins) {
                assertThat(replica1Service.getNode(nodeX.getId()).isDeleted()).isTrue();
                assertThat(replica2Service.getNode(nodeX.getId()).isDeleted()).isTrue();
            } else {
                assertNodeName(nodeX.getId(), "NewName");
                assertThat(replica1Service.getNode(nodeX.getId()).isDeleted()).isFalse();
            }
        }

        @Test
        @DisplayName("10. Vừa đổi tên vừa di chuyển: Phải áp dụng cả hai thay đổi")
        void whenConcurrentRenameAndMove_thenAppliesBothChanges() {
            // Given: A node X and a folder A
            FileNode nodeX = createFolder(replica1Service, "OldName", null);
            FileNode folderA = createFolder(replica1Service, "FolderA", null);
            syncAllReplicas();
            UUID originalParentId = nodeX.getParentId();

            // When: Replica 1 renames X, Replica 2 moves X
            CrdtOperation op1_rename = replica1Service.updateFile(nodeX.getId(), "NewName").getOperation();
            CrdtOperation op2_move = replica2Service.moveFile(nodeX.getId(), folderA.getId()).getOperation();

            // And: Sync
            replica1Service.processExternalOperation(op2_move);
            replica2Service.processExternalOperation(op1_rename);
            replica3Service.processExternalOperation(op1_rename);
            replica3Service.processExternalOperation(op2_move);

            // Then: The final node state should have the new name and the new parent
            // This works because the Kleppmann algorithm's MOVE op carries the name,
            // so the highest timestamp operation will win and set both parent and name.
            CrdtOperation winningOp = op1_rename.getTimestamp() > op2_move.getTimestamp() ? op1_rename : op2_move;

            assertNodeName(nodeX.getId(), winningOp.getNodeName());
            assertNodeParent(nodeX.getId(), winningOp.getParentId());
        }
    }

    @Nested
    @DisplayName("C. Kịch bản Ngăn chặn Chu trình (Cycle Prevention)")
    class CyclePreventionTests {

        @Test
        @DisplayName("11. Tạo chu trình đơn giản: Operation phải bị bỏ qua")
        void whenMovingParentIntoChild_thenOperationIsIgnored() {
            // Given: A nested structure A -> B
            FileNode folderA = createFolder(replica1Service, "A", null);
            FileNode folderB = createFolder(replica1Service, "B", folderA.getId());
            UUID originalParentOfA = folderA.getParentId();

            // When: We try to move A into its child B
            replica1Service.moveFile(folderA.getId(), folderB.getId());
            syncAllReplicas();

            // Then: The parent of A should not have changed
            assertNodeParent(folderA.getId(), originalParentOfA);
        }

        @Test
        @DisplayName("12. Tạo chu trình đồng thời: Phải hội tụ về một cây hợp lệ, không có chu trình")
        void whenConcurrentMovesFormACycle_thenConvergesToAcyclicTree() {
            // Given: Two sibling folders, A and B
            FileNode folderA = createFolder(replica1Service, "A", null);
            FileNode folderB = createFolder(replica1Service, "B", null);
            syncAllReplicas();

            // When: Replica 1 moves B into A, and Replica 2 concurrently moves A into B
            CrdtOperation op1_B_into_A = replica1Service.moveFile(folderB.getId(), folderA.getId()).getOperation();
            CrdtOperation op2_A_into_B = replica2Service.moveFile(folderA.getId(), folderB.getId()).getOperation();

            // And: Replicas sync
            replica1Service.processExternalOperation(op2_A_into_B);
            replica2Service.processExternalOperation(op1_B_into_A);
            replica3Service.processExternalOperation(op1_B_into_A);
            replica3Service.processExternalOperation(op2_A_into_B);

            // Then: Only the operation with the lower timestamp succeeds. The
            // higher-timestamp op is ignored to prevent a cycle.
            CrdtOperation winningOp, losingOp;
            if (op1_B_into_A.getTimestamp() < op2_A_into_B.getTimestamp()) {
                winningOp = op1_B_into_A; // B into A
                losingOp = op2_A_into_B; // A into B
            } else {
                winningOp = op2_A_into_B; // A into B
                losingOp = op1_B_into_A; // B into A
            }

            // The winning operation should be applied
            assertNodeParent(winningOp.getNodeId(), winningOp.getParentId());
            // The losing operation should have been ignored, so its target node's parent is
            // unchanged
            assertNodeParent(losingOp.getNodeId(), losingOp.getOldParentId());
        }
    }

    @Nested
    @DisplayName("D. Kịch bản Xử lý Operation đến không đúng thứ tự (Out-of-Order)")
    class OutOfOrderTests {

        @Test
        @DisplayName("13. Di chuyển xung đột, đến sai thứ tự: Trạng thái cuối cùng phải đúng theo thứ tự timestamp")
        void whenMoveOpsArriveOutOfOrder_thenFinalStateReflectsTimestampOrder() {
            // Given: A node X and two parent folders P1, P2
            FileNode nodeX = createFolder(replica1Service, "X", null);
            FileNode p1 = createFolder(replica1Service, "P1", null);
            FileNode p2 = createFolder(replica1Service, "P2", null);

            // When: Two operations are generated, with op2 having a higher timestamp
            // We use a separate service just for generating ops to control timestamps
            CrdtService generatorService = createReplicaService("generator");
            CrdtOperation op1 = generatorService.moveFile(nodeX.getId(), p1.getId()).getOperation();
            CrdtOperation op2 = generatorService.moveFile(nodeX.getId(), p2.getId()).getOperation();
            assertThat(op2.getTimestamp()).isGreaterThan(op1.getTimestamp());

            // And: A third replica receives them out of order (higher timestamp first)
            replica3Service.processExternalOperation(op2);
            // Verify intermediate state: X is in P2
            assertThat(replica3Service.getNode(nodeX.getId()).getParentId()).isEqualTo(p2.getId());

            // Now process the older operation
            replica3Service.processExternalOperation(op1);

            // Then: The final state must reflect the order of timestamps (op1 then op2), so
            // X should be in P2.
            // The undo-redo logic should have undone op2, applied op1, then re-applied op2.
            assertThat(replica3Service.getNode(nodeX.getId()).getParentId()).isEqualTo(p2.getId());
        }
    }

    @Nested
    @DisplayName("E. Kịch bản Xóa và Khôi phục (Deletion and Undeletion)")
    class DeletionTests {

        @Test
        @DisplayName("14. Khôi phục một thư mục cha: Các con cháu phải xuất hiện trở lại")
        void whenParentIsUndeleted_thenAllDescendantsAreRestored() {
            // Given: A deleted folder A which contains a child B
            FileNode folderA = createFolder(replica1Service, "A", null);
            FileNode folderB = createFolder(replica1Service, "B", folderA.getId());
            replica1Service.deleteFile(folderA.getId());
            syncAllReplicas();

            // Verify it's deleted
            assertThat(fileNodeRepository.findById(folderA.getId()).get().getIsDeleted()).isTrue();
            // The child B should not be visible in the tree structure API
            try {
                String treeAsString = objectMapper.writeValueAsString(replica1Service.getTreeStructure());
                assertThat(treeAsString).doesNotContain(folderB.getId().toString());
            } catch (JsonProcessingException e) {
                fail("Failed to serialize tree structure to JSON during test", e);
            }

            // When: We "undelete" folder A by moving it back to the root
            replica2Service.moveFile(folderA.getId(), null);
            syncAllReplicas();

            // Then: Folder A should no longer be deleted
            FileNode finalA = fileNodeRepository.findById(folderA.getId()).get();
            assertThat(finalA.getIsDeleted()).isFalse();
            assertThat(finalA.getParentId()).isNotEqualTo(com.crdt.crdt.CrdtTree.TRASH_ROOT_ID);

            // And: Its child B should now be visible and correctly parented
            FileNode finalB = fileNodeRepository.findById(folderB.getId()).get();
            assertThat(finalB.getIsDeleted()).isFalse();
            assertThat(finalB.getParentId()).isEqualTo(folderA.getId());
            assertThat(replica1Service.getNode(folderB.getId())).isNotNull();
        }

        @Test
        @DisplayName("15. Xóa và tạo lại với cùng tên: Phải tạo một node mới hoàn toàn")
        void whenNodeIsDeletedAndRecreated_thenNewNodeIsCreated() {
            // Given: A folder is created and then deleted
            FileNode originalNode = createFolder(replica1Service, "MyFolder", null);
            replica1Service.deleteFile(originalNode.getId());
            syncAllReplicas();

            // When: A new folder with the same name and parent is created on another
            // replica
            FileNode newNode = createFolder(replica2Service, "MyFolder", null);
            syncAllReplicas();

            // Then: The new node should have a different ID from the original one
            assertThat(newNode.getId()).isNotEqualTo(originalNode.getId());

            // And: The new node should be active and not deleted
            assertThat(fileNodeRepository.findById(newNode.getId()).get().getIsDeleted()).isFalse();
            assertThat(replica1Service.getNode(newNode.getId()).isDeleted()).isFalse();

            // And: The old node should remain deleted
            assertThat(fileNodeRepository.findById(originalNode.getId()).get().getIsDeleted()).isTrue();
            assertThat(replica1Service.getNode(originalNode.getId()).isDeleted()).isTrue();
        }
    }
}
