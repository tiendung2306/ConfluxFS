package com.crdt.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.crdt.dto.CrdtOperationDto;
import com.crdt.dto.SyncResponse;
import com.crdt.model.CrdtOperation;
import com.crdt.service.CrdtService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/crdt")
@RequiredArgsConstructor
public class CrdtController {

    private final CrdtService crdtService;

    @PostMapping("/operations")
    public ResponseEntity<?> submitOperation(@RequestBody CrdtOperationDto operationDto) {
        try {
            // Convert DTO to entity and process
            CrdtOperation operation = convertToEntity(operationDto);
            crdtService.processExternalOperation(operation);
            return ResponseEntity.ok("Operation processed successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/state")
    public ResponseEntity<?> getCrdtState() {
        try {
            Map<String, Object> state = crdtService.getTreeStructure();
            return ResponseEntity.ok(state);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/sync")
    public ResponseEntity<?> syncWithReplicas() {
        try {
            crdtService.syncWithReplicas();
            return ResponseEntity.ok("Sync completed successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/operations")
    public ResponseEntity<?> getOperations(@RequestParam(required = false) Long since) {
        try {
            List<CrdtOperation> operations = crdtService.getOperationsSince(since);
            List<CrdtOperationDto> dtos = operations.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

            SyncResponse response = new SyncResponse();
            response.setReplicaId(crdtService.getReplicaId());
            response.setVectorClock(crdtService.getVectorClock());
            response.setOperations(dtos);
            response.setHasMore(false); // Simplified for now

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private CrdtOperation convertToEntity(CrdtOperationDto dto) {
        CrdtOperation operation = new CrdtOperation();
        operation.setId(dto.getId());
        operation.setType(CrdtOperation.OperationType.valueOf(dto.getType()));
        operation.setNodeId(dto.getNodeId());
        operation.setParentId(dto.getParentId());
        operation.setOldParentId(dto.getOldParentId());
        operation.setNodeName(dto.getNodeName());
        if (dto.getNodeType() != null) {
            operation.setNodeType(com.crdt.model.FileNode.FileType.valueOf(dto.getNodeType()));
        }
        operation.setReplicaId(dto.getReplicaId());
        operation.setTimestamp(dto.getTimestamp());
        operation.setVectorClock(dto.getVectorClock());
        operation.setIsApplied(dto.isApplied());
        return operation;
    }

    private CrdtOperationDto convertToDto(CrdtOperation operation) {
        CrdtOperationDto dto = new CrdtOperationDto();
        dto.setId(operation.getId());
        dto.setType(operation.getType().toString());
        dto.setNodeId(operation.getNodeId());
        dto.setParentId(operation.getParentId());
        dto.setOldParentId(operation.getOldParentId());
        dto.setNodeName(operation.getNodeName());
        if (operation.getNodeType() != null) {
            dto.setNodeType(operation.getNodeType().toString());
        }
        dto.setReplicaId(operation.getReplicaId());
        dto.setTimestamp(operation.getTimestamp());
        dto.setVectorClock(operation.getVectorClock());
        dto.setApplied(operation.getIsApplied());
        return dto;
    }
}
