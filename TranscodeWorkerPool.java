package com.sudesh.transcodequeue.service;

import com.sudesh.transcodequeue.repository.TranscodeJobRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns the fixed-size pool of transcode workers. WORKER_COUNT stands in for
 * "how many worker containers/instances are running" - in production this
 * would instead be N separate containers each consuming from a shared
 * SQS/RabbitMQ queue, but the failure/recovery behavior is identical.
 */
@Component
public class TranscodeWorkerPool {

    private final JobQueueService queueService;
    private final TranscodeJobRepository jobRepository;
    private final WorkerRegistry registry;
    private final int workerCount;

    private ExecutorService executor;
    private final List<TranscodeWorkerTask> tasks = new ArrayList<>();

    public TranscodeWorkerPool(JobQueueService queueService,
                                TranscodeJobRepository jobRepository,
                                WorkerRegistry registry,
                                @Value("${transcode.worker-count:3}") int workerCount) {
        this.queueService = queueService;
        this.jobRepository = jobRepository;
        this.registry = registry;
        this.workerCount = workerCount;
        start();
    }

    private void start() {
        executor = Executors.newFixedThreadPool(workerCount);
        for (int i = 1; i <= workerCount; i++) {
            String workerId = "worker-" + i;
            TranscodeWorkerTask task = new TranscodeWorkerTask(workerId, queueService, jobRepository, registry);
            tasks.add(task);
            executor.submit(task);
        }
    }

    public int getWorkerCount() {
        return workerCount;
    }

    @PreDestroy
    public void shutdown() {
        tasks.forEach(TranscodeWorkerTask::stop);
        executor.shutdownNow();
    }
}
