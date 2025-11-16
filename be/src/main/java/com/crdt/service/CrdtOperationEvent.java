package com.crdt.service;

import org.springframework.context.ApplicationEvent;

import java.io.Serial;

public class CrdtOperationEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;
    private final String eventType;
    private final Object data;

    public CrdtOperationEvent(Object source, String eventType, Object data) {
        super(source);
        this.eventType = eventType;
        this.data = data;
    }

    public String getEventType() {
        return eventType;
    }

    public Object getData() {
        return data;
    }
}
