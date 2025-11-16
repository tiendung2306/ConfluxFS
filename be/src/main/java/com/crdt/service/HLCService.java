package com.crdt.service;

import com.crdt.util.HybridLogicalClock;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class HLCService {

    // AtomicReference to ensure thread-safe updates to the clock.
    private final AtomicReference<HybridLogicalClock> latestHlc;

    public HLCService() {
        // Initialize the clock with the current time and a counter of 0.
        this.latestHlc = new AtomicReference<>(new HybridLogicalClock(System.currentTimeMillis(), 0));
    }

    /**
     * Generates a new, unique timestamp for a local event.
     * This method is thread-safe.
     * @return A new HLC timestamp that is guaranteed to be greater than any previously generated or received timestamp.
     */
    public synchronized HybridLogicalClock newTimestamp() {
        long wallClock = System.currentTimeMillis();
        HybridLogicalClock nextHlc = HybridLogicalClock.tick(this.latestHlc.get(), wallClock);
        this.latestHlc.set(nextHlc);
        return nextHlc;
    }

    /**
     * Updates the local clock with a timestamp received from a remote replica.
     * This is essential for tracking causality across the distributed system.
     * This method is thread-safe.
     * @param remoteTimestamp The HLC timestamp from the remote operation.
     * @return The new, updated local HLC timestamp.
     */
    public synchronized HybridLogicalClock updateWithRemoteTimestamp(HybridLogicalClock remoteTimestamp) {
        long wallClock = System.currentTimeMillis();
        HybridLogicalClock updatedHlc = HybridLogicalClock.update(this.latestHlc.get(), remoteTimestamp, wallClock);
        this.latestHlc.set(updatedHlc);
        return updatedHlc;
    }

    public HybridLogicalClock getLatestHlc() {
        return this.latestHlc.get();
    }
}
