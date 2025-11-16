package com.crdt.controller;

import com.crdt.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.HashMap;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("message", ex.getMessage());
        body.put("error", "Not Found");
        body.put("status", HttpStatus.NOT_FOUND.value());
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.error("File upload size exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body("File size exceeds maximum allowed size (100MB). Please choose a smaller file.");
    }

    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    public ResponseEntity<String> handleMultipartException(org.springframework.web.multipart.MultipartException ex) {
        log.error("Multipart error: {}", ex.getMessage());
        if (ex.getCause().getCause() instanceof MaxUploadSizeExceededException) {
            return handleMaxUploadSizeExceeded((MaxUploadSizeExceededException) ex.getCause().getCause());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("File upload failed: " + ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGlobalException(Exception ex, WebRequest request) {
        log.error("An unexpected error occurred: {}", ex.getMessage(), ex);
        Map<String, Object> body = new HashMap<>();
        body.put("message", "An internal server error occurred.");
        body.put("error", "Internal Server Error");
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

