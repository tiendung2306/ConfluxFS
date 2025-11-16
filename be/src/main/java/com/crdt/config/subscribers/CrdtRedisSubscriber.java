package com.crdt.config.subscribers;

import org.springframework.stereotype.Component;

import com.crdt.model.CrdtOperation;
import com.crdt.service.CrdtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class CrdtRedisSubscriber {

    private final CrdtService crdtService;

    // Invoked by MessageListenerAdapter via Redis subscription
    public void handleMessage(CrdtOperation operation) {
        if (operation == null) {
            log.warn("Received a null CRDT operation from Redis.");
            return;
        }
        try {
            log.debug("Received CRDT operation from Redis: {}", operation);
            crdtService.processExternalOperation(operation);
        } catch (Exception e) {
            log.error("Error processing CRDT operation {} from Redis: {}", operation.getId(), e.getMessage(), e);
        }
    }
}


