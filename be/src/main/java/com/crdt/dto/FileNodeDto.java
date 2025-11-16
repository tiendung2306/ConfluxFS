package com.crdt.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileNodeDto {
    private UUID id;
    private UUID parentId;
    private String name;
    private String type;
    private Long fileSize;
    private String mimeType;
    private Long timestamp;
    private String replicaId;
    private boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
