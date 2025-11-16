package com.crdt;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.crdt.service.CrdtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class CrdtFileSystemApplication implements CommandLineRunner {

    private final CrdtService crdtService;

    public static void main(String[] args) {
        SpringApplication.run(CrdtFileSystemApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Initializing CRDT File System...");
        crdtService.initializeCrdtTree();
        log.info("CRDT File System initialized successfully");
    }

    /**
     * Sync with other replicas every 30 seconds
     */
    @Scheduled(fixedRate = 30000)
    public void syncWithReplicas() {
        try {
            crdtService.syncWithReplicas();
        } catch (Exception e) {
            log.error("Error during sync: {}", e.getMessage());
        }
    }
}
