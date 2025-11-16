package com.crdt.repository;

import com.crdt.model.CrdtOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CrdtOperationRepository extends JpaRepository<CrdtOperation, UUID> {

    List<CrdtOperation> findByReplicaIdOrderByTimestamp(String replicaId);

    List<CrdtOperation> findByTimestampGreaterThanOrderByTimestamp(Long timestamp);

    List<CrdtOperation> findByIsAppliedFalseOrderByTimestamp();

    @Query("SELECT o FROM CrdtOperation o WHERE o.replicaId != :replicaId AND o.timestamp > :timestamp ORDER BY o.timestamp")
    List<CrdtOperation> findOperationsFromOtherReplicasAfterTimestamp(
            @Param("replicaId") String replicaId,
            @Param("timestamp") Long timestamp);

    @Query("SELECT MAX(o.timestamp) FROM CrdtOperation o WHERE o.replicaId = :replicaId")
    Long findMaxTimestampByReplicaId(@Param("replicaId") String replicaId);

    List<CrdtOperation> findByNodeIdOrderByTimestamp(UUID nodeId);

    // New method for the improved sync logic
    List<CrdtOperation> findByReplicaIdAndTimestampGreaterThanOrderByTimestamp(String replicaId, long timestamp);

    // New method to discover all replicas in the system
    @Query("SELECT DISTINCT o.replicaId FROM CrdtOperation o")
    List<String> findDistinctReplicaIds();
}
