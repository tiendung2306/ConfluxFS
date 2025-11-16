package com.crdt.crdt;

import java.util.UUID;

import com.crdt.model.FileNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a node in the CRDT Tree
 * Each node contains metadata and CRDT-specific information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TreeNode {

    private UUID id;
    private UUID parentId;
    private String name;
    private FileNode.FileType type;
    private Long timestamp;
    private String replicaId;
    private boolean deleted;

    public TreeNode(TreeNode other) {
        this.id = other.id;
        this.parentId = other.parentId;
        this.name = other.name;
        this.type = other.type;
        this.timestamp = other.timestamp;
        this.replicaId = other.replicaId;
        this.deleted = other.deleted;
    }

    /**
     * Check if this node is a root node (no parent)
     */
    public boolean isRoot() {
        return parentId == null;
    }

    /**
     * Check if this node is a folder
     */
    public boolean isFolder() {
        return type == FileNode.FileType.FOLDER;
    }

    /**
     * Check if this node is a file
     */
    public boolean isFile() {
        return type == FileNode.FileType.FILE;
    }
}
