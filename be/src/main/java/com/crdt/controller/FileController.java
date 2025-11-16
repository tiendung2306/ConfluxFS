package com.crdt.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.crdt.dto.CreateFolderRequest;
import com.crdt.dto.CopyFileRequest;
import com.crdt.dto.FileNodeDto;
import com.crdt.dto.MoveFileRequest;
import com.crdt.dto.UpdateFileRequest;
import com.crdt.model.FileNode;
import com.crdt.model.User;
import com.crdt.service.CrdtService;
import com.crdt.service.FileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Tag(name = "File Management", description = "File and folder operations APIs")
@SecurityRequirement(name = "bearerAuth")
public class FileController {

    private final FileService fileService;
    private final CrdtService crdtService;

    @GetMapping("/tree")
    @Operation(summary = "Get file tree", description = "Retrieve the complete file system tree structure")
    @ApiResponse(responseCode = "200", description = "File tree retrieved successfully")
    public ResponseEntity<?> getFileTree(Authentication authentication) {
        Map<String, Object> treeStructure = crdtService.getTreeStructure();
        return ResponseEntity.ok(treeStructure);
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload file", description = "Upload a new file to the file system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request or upload failed"),
            @ApiResponse(responseCode = "413", description = "File size exceeds maximum allowed size")
    })
    public ResponseEntity<?> uploadFile(
            @Parameter(description = "File to upload") @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @Parameter(description = "Parent folder ID") @RequestParam(value = "parentId", required = false) String parentId,
            Authentication authentication) {
        try {
            User user = (User) authentication.getPrincipal();
            java.util.UUID parentUuid = (parentId == null || parentId.isBlank()) ? null : java.util.UUID.fromString(parentId);
            FileNode uploadedFile = fileService.uploadFile(file, parentUuid, user.getId());

            FileNodeDto dto = convertToDto(uploadedFile);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("size")) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE)
                        .body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Upload failed: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadFile(@PathVariable UUID id, Authentication authentication) {
        try {
            User user = (User) authentication.getPrincipal();
            return fileService.downloadFile(id, user.getId());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/folder")
    public ResponseEntity<?> createFolder(@Valid @RequestBody CreateFolderRequest request,
            Authentication authentication) {
        try {
            User user = (User) authentication.getPrincipal();
            FileNode folder = crdtService.createFolder(request.getName(), request.getParentId(), user.getId());

            FileNodeDto dto = convertToDto(folder);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateFile(@PathVariable UUID id, @Valid @RequestBody UpdateFileRequest request,
            Authentication authentication) {
        try {
            FileNode updatedFile = crdtService.updateFile(id, request.getName());

            FileNodeDto dto = convertToDto(updatedFile);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable UUID id, Authentication authentication) {
        try {
            crdtService.deleteFile(id);
            return ResponseEntity.ok("File deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/move")
    public ResponseEntity<?> moveFile(@PathVariable UUID id, @Valid @RequestBody MoveFileRequest request,
            Authentication authentication) {
        try {
            FileNode movedFile = crdtService.moveFile(id, request.getNewParentId());

            FileNodeDto dto = convertToDto(movedFile);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/copy")
    public ResponseEntity<?> copyFile(@PathVariable UUID id, @Valid @RequestBody CopyFileRequest request,
            Authentication authentication) {
        try {
            User user = (User) authentication.getPrincipal();
            FileNode copied = crdtService.copyNode(id, request.getTargetParentId(), user.getId());
            FileNodeDto dto = convertToDto(copied);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}/children")
    public ResponseEntity<?> getChildren(@PathVariable UUID id, Authentication authentication) {
        try {
            User user = (User) authentication.getPrincipal();
            List<FileNode> children = fileService.getChildren(id, user.getId());

            List<FileNodeDto> dtos = children.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private FileNodeDto convertToDto(FileNode fileNode) {
        FileNodeDto dto = new FileNodeDto();
        dto.setId(fileNode.getId());
        dto.setParentId(fileNode.getParentId());
        dto.setName(fileNode.getName());
        dto.setType(fileNode.getType().toString());
        dto.setFileSize(fileNode.getFileSize());
        dto.setMimeType(fileNode.getMimeType());
        dto.setTimestamp(fileNode.getTimestamp());
        dto.setReplicaId(fileNode.getReplicaId());
        dto.setDeleted(fileNode.getIsDeleted());
        dto.setCreatedAt(fileNode.getCreatedAt());
        dto.setUpdatedAt(fileNode.getUpdatedAt());
        return dto;
    }
}
