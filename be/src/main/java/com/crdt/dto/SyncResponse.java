package com.crdt.dto;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncResponse {
    private String replicaId;
    private Map<String, Long> vectorClock;
    private List<CrdtOperationDto> operations;
    private boolean hasMore;
}
