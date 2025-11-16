package com.crdt.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CrdtEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public CrdtEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCrdtOperationEvent(CrdtOperationEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", event.getEventType());
        payload.put("data", event.getData());
        messagingTemplate.convertAndSend("/topic/events", payload);
    }
}
