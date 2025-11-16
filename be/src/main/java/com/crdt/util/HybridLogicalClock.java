package com.crdt.util;

import java.io.Serializable;
import java.util.Objects;

/**
 * Hybrid Logical Clock (HLC) implementation.
 * Combines physical time (wall-clock) with a logical counter to ensure
 * monotonic increase and track causality.
 * The timestamp is encoded into a single 64-bit long:
 * - 48 bits for milliseconds since epoch.
 * - 16 bits for a counter to handle events within the same millisecond.
 */
public class HybridLogicalClock implements Comparable<HybridLogicalClock>, Serializable {

    private static final int COUNTER_BITS = 16;
    private static final long MAX_COUNTER = (1L << COUNTER_BITS) - 1;
    private static final long TIMESTAMP_MASK = (1L << (64 - COUNTER_BITS)) - 1;

    private final long hlc;

    public HybridLogicalClock(long physicalTime, int counter) {
        if (counter > MAX_COUNTER) {
            throw new IllegalArgumentException("Counter exceeds maximum value of " + MAX_COUNTER);
        }
        this.hlc = (physicalTime << COUNTER_BITS) | counter;
    }

    private HybridLogicalClock(long hlc) {
        this.hlc = hlc;
    }

    public static HybridLogicalClock fromLong(long hlc) {
        return new HybridLogicalClock(hlc);
    }

    public long asLong() {
        return hlc;
    }

    public long getPhysicalTime() {
        return hlc >>> COUNTER_BITS;
    }

    public int getCounter() {
        return (int) (hlc & MAX_COUNTER);
    }

    @Override
    public int compareTo(HybridLogicalClock other) {
        return Long.compare(this.hlc, other.hlc);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HybridLogicalClock that = (HybridLogicalClock) o;
        return hlc == that.hlc;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hlc);
    }

    @Override
    public String toString() {
        return getPhysicalTime() + "-" + getCounter();
    }

    /**
     * The core logic of HLC. Generates the next timestamp based on the current clock state
     * and the physical wall-clock time.
     * @param latestHlc The latest timestamp known by the node.
     * @param wallClock The current physical time (System.currentTimeMillis()).
     * @return The next, guaranteed-to-be-later HLC timestamp.
     */
    public static HybridLogicalClock tick(HybridLogicalClock latestHlc, long wallClock) {
        long latestPhysical = latestHlc.getPhysicalTime();
        
        // Case 1: Physical clock is ahead of our latest timestamp.
        // We can use the physical clock time and reset the counter.
        if (wallClock > latestPhysical) {
            return new HybridLogicalClock(wallClock, 0);
        }

        // Case 2: Physical clock is behind or same as our latest timestamp.
        // This can happen due to clock skew or rapid events.
        // We must use our latest timestamp and increment the counter to ensure monotonicity.
        int newCounter = latestHlc.getCounter() + 1;
        if (newCounter > MAX_COUNTER) {
            // This is a rare "clock overflow" scenario. We must advance the physical time part by 1ms.
            return new HybridLogicalClock(latestPhysical + 1, 0);
        }
        return new HybridLogicalClock(latestPhysical, newCounter);
    }

    /**
     * Updates the local clock based on a received timestamp from a remote replica.
     * This is crucial for tracking causality.
     * @param localHlc The current latest timestamp of the local node.
     * @param remoteHlc The timestamp received from the remote node.
     * @param wallClock The current physical time.
     * @return The new, updated local timestamp.
     */
    public static HybridLogicalClock update(HybridLogicalClock localHlc, HybridLogicalClock remoteHlc, long wallClock) {
        long localPhysical = localHlc.getPhysicalTime();
        long remotePhysical = remoteHlc.getPhysicalTime();

        // The new physical time is the max of local, remote, and wall-clock time.
        long newPhysical = Math.max(Math.max(localPhysical, remotePhysical), wallClock);

        int newCounter;
        if (newPhysical == localPhysical && newPhysical == remotePhysical) {
            // All three clocks have the same physical time part, take the max counter and increment.
            newCounter = Math.max(localHlc.getCounter(), remoteHlc.getCounter()) + 1;
        } else if (newPhysical == localPhysical) {
            // New time matches local time, increment local counter.
            newCounter = localHlc.getCounter() + 1;
        } else if (newPhysical == remotePhysical) {
            // New time matches remote time, increment remote counter.
            newCounter = remoteHlc.getCounter() + 1;
        } else {
            // New time is the wall-clock time, reset counter.
            newCounter = 0;
        }

        if (newCounter > MAX_COUNTER) {
            return new HybridLogicalClock(newPhysical + 1, 0);
        }
        
        return new HybridLogicalClock(newPhysical, newCounter);
    }
}
