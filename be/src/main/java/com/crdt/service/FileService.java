package com.crdt.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.crdt.exception.ResourceNotFoundException;
import com.crdt.model.FileNode;
import com.crdt.repository.FileNodeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileNodeRepository fileNodeRepository;
    private final CrdtService crdtService;

    @Value("${file.storage.path}")
    private String storagePath;

    @Value("${file.storage.max-size}")
    private long maxFileSize;

    public FileNode uploadFile(MultipartFile file, UUID parentId, UUID userId) throws IOException {
        if (file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }

        if (file.getSize() > maxFileSize) {
            throw new RuntimeException("File size exceeds maximum allowed size");
        }

        // Save file to storage
        String fileName = java.util.UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path filePath = Paths.get(storagePath).resolve(fileName);
        Files.createDirectories(filePath.getParent());
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Register file via CRDT (creates DB node + broadcasts)
        return crdtService.createFile(
                file.getOriginalFilename(),
                parentId,
                file.getSize(),
                file.getContentType(),
                filePath.toString(),
                userId).getFileNode();
    }

    public static class DuplicateResult {
        public String filePath;
        public long size;

        public DuplicateResult(String filePath, long size) {
            this.filePath = filePath;
            this.size = size;
        }
    }

    public DuplicateResult duplicateFile(String sourceFilePath, String originalFilename) throws IOException {
        return FileService.duplicateFileStatic(sourceFilePath, originalFilename, storagePath);
    }

    public static DuplicateResult duplicateFileStatic(String sourceFilePath, String originalFilename,
            String storagePath) throws IOException {
        Path source = Paths.get(sourceFilePath);
        if (!Files.exists(source)) {
            throw new IOException("Source file not found: " + sourceFilePath);
        }
        String newName = java.util.UUID.randomUUID().toString() + "_" + originalFilename;
        Path target = Paths.get(storagePath).resolve(newName);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        long size = Files.size(target);
        return new DuplicateResult(target.toString(), size);
    }

    public ResponseEntity<Resource> downloadFile(UUID fileId, UUID userId) throws IOException {
        FileNode fileNode = fileNodeRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));

        if (fileNode.getIsDeleted()) {
            throw new ResourceNotFoundException("File has been deleted: " + fileId);
        }

        Path filePath = Paths.get(fileNode.getFilePath());
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists()) {
            throw new ResourceNotFoundException("File not found on disk for id: " + fileId);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fileNode.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileNode.getName() + "\"")
                .body(resource);
    }

    public List<FileNode> getChildren(UUID parentId, UUID userId) {
        return fileNodeRepository.findByParentIdAndIsDeletedFalseOrderByName(parentId);
    }

    public List<FileNode> getUserFiles(UUID userId) {
        return fileNodeRepository.findByOwnerIdAndIsDeletedFalseOrderByName(userId);
    }
}
