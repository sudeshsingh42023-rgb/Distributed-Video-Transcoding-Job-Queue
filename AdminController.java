package com.sudesh.transcodequeue.controller;

import com.sudesh.transcodequeue.service.WorkerRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    /** How long a "crashed" worker stays down before it's auto-restarted, simulating an orchestrator (ECS/K8s) bringing the container back. */
    private static final long RESTART_DELAY_SECONDS = 8;

    private final WorkerRegistry registry;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public AdminController(WorkerRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/workers")
    public Map<String, Object> workerStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        registry.allWorkers().forEach((id, stats) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("crashed", stats.crashed);
            entry.put("currentJobId", stats.currentJobId);
            entry.put("jobsCompleted", stats.jobsCompleted.get());
            entry.put("jobsFailed", stats.jobsFailed.get());
            entry.put("secondsSinceHeartbeat", Duration.between(stats.lastHeartbeat, Instant.now()).getSeconds());
            result.put(id, entry);
        });
        return result;
    }

    /**
     * Chaos-engineering endpoint: simulates a hard crash of one worker
     * (container OOM-kill / node loss). The worker stops picking up new
     * jobs immediately; whatever job it was mid-processing is recovered by
     * {@code JobRecoveryWatchdog} via the visibility timeout, independent of
     * this endpoint. The worker "self-heals" after RESTART_DELAY_SECONDS,
     * simulating an orchestrator restarting the container.
     */
    @PostMapping("/workers/{workerId}/crash")
    public ResponseEntity<Map<String, Object>> crashWorker(@PathVariable String workerId) {
        registry.markCrashed(workerId);
        scheduler.schedule(() -> registry.markRecovered(workerId), RESTART_DELAY_SECONDS, TimeUnit.SECONDS);

        return ResponseEntity.ok(Map.of(
                "workerId", workerId,
                "status", "crashed",
                "willRestartInSeconds", RESTART_DELAY_SECONDS
        ));
    }
}
