package com.crdt.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "crdt_operations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CrdtOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "old_parent_id")
    private UUID oldParentId;

    @Column(name = "node_name")
    private String nodeName;

    @Column(name = "old_node_name")
    private String oldNodeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type")
    private FileNode.FileType nodeType;

    @Column(name = "replica_id", nullable = false)
    private String replicaId;

    @Column(name = "timestamp", nullable = false)
    private Long timestamp;

    @Column(name = "vector_clock", columnDefinition = "TEXT")
    private String vectorClock;

    @Column(name = "operation_data", columnDefinition = "TEXT")
    private String operationData;

    @Column(name = "is_applied")
    private Boolean isApplied = false;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

}
