package com.crdt.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.crdt.model.FileNode;

@Repository
public interface FileNodeRepository extends JpaRepository<FileNode, UUID> {

    List<FileNode> findByParentIdAndIsDeletedFalseOrderByName(UUID parentId);

    List<FileNode> findByOwnerIdAndIsDeletedFalseOrderByName(UUID ownerId);

    List<FileNode> findByParentIdIsNullAndIsDeletedFalseOrderByName();

    @Query("SELECT f FROM FileNode f WHERE f.parentId = :parentId AND f.name = :name AND f.isDeleted = false")
    Optional<FileNode> findByParentIdAndNameAndNotDeleted(@Param("parentId") UUID parentId, @Param("name") String name);

    @Query("SELECT f FROM FileNode f WHERE f.parentId = :parentId AND f.name = :name AND f.isDeleted = false AND f.id != :excludeId")
    Optional<FileNode> findByParentIdAndNameAndNotDeletedExcludingId(
            @Param("parentId") UUID parentId,
            @Param("name") String name,
            @Param("excludeId") UUID excludeId);

    List<FileNode> findByReplicaIdAndIsDeletedFalse(String replicaId);

    @Query("SELECT f FROM FileNode f WHERE f.timestamp > :timestamp ORDER BY f.timestamp")
    List<FileNode> findNodesModifiedAfter(@Param("timestamp") Long timestamp);
}
