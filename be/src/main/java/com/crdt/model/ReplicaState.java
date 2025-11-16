package com.crdt.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "replica_states")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplicaState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "replica_id", unique = true, nullable = false)
    private String replicaId;

    @Column(name = "last_operation_timestamp")
    private Long lastOperationTimestamp;

    @Column(name = "vector_clock", columnDefinition = "TEXT")
    private String vectorClock;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
