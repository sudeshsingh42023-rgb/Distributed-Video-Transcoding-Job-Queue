package com.sudesh.transcodequeue.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bookkeeping for the worker pool: last-heartbeat time and job counters per
 * worker id, plus a set of workerIds that have been deliberately "crashed"
 * via the admin endpoint (for demoing failure detection). This is the piece
 * a real deployment would replace with per-container health checks reported
 * to an orchestrator (ECS/Kubernetes) instead of an in-JVM map.
 */
@Service
public class WorkerRegistry {

    public static class WorkerStats {
        public volatile Instant lastHeartbeat = Instant.now();
        public final AtomicInteger jobsCompleted = new AtomicInteger(0);
        public final AtomicInteger jobsFailed = new AtomicInteger(0);
        public volatile boolean crashed = false;
        public volatile String currentJobId = null;
    }

    private final Map<String, WorkerStats> workers = new ConcurrentHashMap<>();

    public WorkerStats statsFor(String workerId) {
        return workers.computeIfAbsent(workerId, id -> new WorkerStats());
    }

    public void heartbeat(String workerId, String currentJobId) {
        WorkerStats stats = statsFor(workerId);
        stats.lastHeartbeat = Instant.now();
        stats.currentJobId = currentJobId;
    }

    public void markCrashed(String workerId) {
        statsFor(workerId).crashed = true;
    }

    public void markRecovered(String workerId) {
        WorkerStats stats = statsFor(workerId);
        stats.crashed = false;
        stats.currentJobId = null;
        stats.lastHeartbeat = Instant.now();
    }

    public boolean isCrashed(String workerId) {
        return statsFor(workerId).crashed;
    }

    public Map<String, WorkerStats> allWorkers() {
        return workers;
    }
}
