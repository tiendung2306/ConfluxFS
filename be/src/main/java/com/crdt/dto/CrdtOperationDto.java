package com.crdt.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrdtOperationDto {
    private UUID id;
    private String type;
    private UUID nodeId;
    private UUID parentId;
    private UUID oldParentId;
    private String nodeName;
    private String nodeType;
    private String replicaId;
    private Long timestamp;
    private String vectorClock;
    private boolean isApplied;
}
