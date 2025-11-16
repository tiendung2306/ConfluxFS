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
@Table(name = "files")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileNode {

    @Id
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileType type;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "replica_id")
    private String replicaId;

    @Column(name = "timestamp")
    private Long timestamp;

    @Column(name = "vector_clock", columnDefinition = "TEXT")
    private String vectorClock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum FileType {
        FILE, FOLDER
    }
}
