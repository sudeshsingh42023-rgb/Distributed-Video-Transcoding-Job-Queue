package com.sudesh.transcodequeue.service;

import com.sudesh.transcodequeue.model.TranscodeJob;
import com.sudesh.transcodequeue.repository.TranscodeJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * The unit of work run by each thread in the worker pool. In production this
 * is where an FFmpeg invocation would live (e.g. shelling out to
 * `ffmpeg -i {sourceUrl} -vf scale=... {outputPath}` and uploading the result
 * to S3); here it's simulated with a sleep so the failure/recovery machinery
 * can be demonstrated without needing real media files or an FFmpeg binary
 * on the grading/interview machine.
 */
public class TranscodeWorkerTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TranscodeWorkerTask.class);
    private static final Random RANDOM = new Random();

    /** How long a job may sit in PROCESSING before the watchdog assumes its worker died. */
    public static final long VISIBILITY_TIMEOUT_SECONDS = 10;

    private final String workerId;
    private final JobQueueService queueService;
    private final TranscodeJobRepository jobRepository;
    private final WorkerRegistry registry;
    private volatile boolean running = true;

    public TranscodeWorkerTask(String workerId,
                                JobQueueService queueService,
                                TranscodeJobRepository jobRepository,
                                WorkerRegistry registry) {
        this.workerId = workerId;
        this.queueService = queueService;
        this.jobRepository = jobRepository;
        this.registry = registry;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        log.info("Worker {} started", workerId);
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                if (registry.isCrashed(workerId)) {
                    // Simulates the container being down; an orchestrator would
                    // restart it - here WorkerRegistry.markRecovered() does that
                    // on a timer set by the admin/chaos endpoint.
                    Thread.sleep(1000);
                    continue;
                }

                Long jobId = queueService.take(2, TimeUnit.SECONDS);
                if (jobId == null) {
                    registry.heartbeat(workerId, null);
                    continue;
                }

                processJob(jobId);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Worker {} stopped", workerId);
    }

    private void processJob(Long jobId) {
        TranscodeJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;

        job.setStatus(TranscodeJob.Status.PROCESSING);
        job.setWorkerId(workerId);
        job.setPickedUpAt(Instant.now());
        jobRepository.save(job);
        registry.heartbeat(workerId, String.valueOf(jobId));
        log.info("Worker {} picked up job {} ({} -> {})", workerId, jobId, job.getSourceUrl(), job.getTargetFormat());

        try {
            simulateTranscode();
        } catch (WorkerCrashedException crash) {
            // Deliberately do NOT touch the job row here - a real crash gives
            // you no chance to run cleanup code. The watchdog's visibility
            // timeout is what recovers this job, not this catch block.
            log.warn("Worker {} crashed while processing job {} - abandoning without update", workerId, jobId);
            return;
        } catch (Exception transcodeFailure) {
            handleFailure(job, transcodeFailure.getMessage());
            return;
        }

        job.setStatus(TranscodeJob.Status.DONE);
        job.setOutputUrl("s3://transcoded-output/job-" + job.getId() + "." + extensionFor(job.getTargetFormat()));
        jobRepository.save(job);
        registry.statsFor(workerId).jobsCompleted.incrementAndGet();
        registry.heartbeat(workerId, null);
        log.info("Worker {} finished job {} -> {}", workerId, jobId, job.getOutputUrl());
    }

    private void handleFailure(TranscodeJob job, String errorMessage) {
        int attempts = job.getAttempts() + 1;
        job.setAttempts(attempts);
        job.setLastError(errorMessage);
        registry.statsFor(workerId).jobsFailed.incrementAndGet();

        if (attempts < job.getMaxAttempts()) {
            job.setStatus(TranscodeJob.Status.QUEUED);
            jobRepository.save(job);
            queueService.enqueue(job.getId());
            log.warn("Job {} failed (attempt {}/{}) - requeued: {}", job.getId(), attempts, job.getMaxAttempts(), errorMessage);
        } else {
            job.setStatus(TranscodeJob.Status.DEAD_LETTER);
            jobRepository.save(job);
            log.error("Job {} exhausted {} attempts - moved to dead-letter: {}", job.getId(), job.getMaxAttempts(), errorMessage);
        }
        registry.heartbeat(workerId, null);
    }

    /** Sleeps in short increments so a mid-flight crash flag is noticed quickly, and randomly fails ~15% of the time. */
    private void simulateTranscode() throws InterruptedException {
        int totalSteps = 3 + RANDOM.nextInt(3); // 3-5 steps of 700ms ~= 2-3.5s "processing"
        for (int i = 0; i < totalSteps; i++) {
            Thread.sleep(700);
            if (registry.isCrashed(workerId)) {
                throw new WorkerCrashedException(workerId);
            }
        }
        if (RANDOM.nextInt(100) < 15) {
            throw new RuntimeException("Simulated transcode error (e.g. corrupt source frame)");
        }
    }

    private String extensionFor(String targetFormat) {
        if (targetFormat == null) return "mp4";
        String lower = targetFormat.toLowerCase();
        if (lower.contains("webm")) return "webm";
        if (lower.contains("mov")) return "mov";
        return "mp4";
    }
}
