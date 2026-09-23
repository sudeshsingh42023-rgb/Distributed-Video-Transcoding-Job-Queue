package com.sudesh.transcodequeue.scheduling;

import com.sudesh.transcodequeue.model.TranscodeJob;
import com.sudesh.transcodequeue.repository.TranscodeJobRepository;
import com.sudesh.transcodequeue.service.JobQueueService;
import com.sudesh.transcodequeue.service.TranscodeWorkerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * This is the core "failure detection + failover" mechanism: any job that
 * has been sitting in PROCESSING for longer than the visibility timeout is
 * assumed to belong to a worker that died mid-job (crashed, OOM-killed,
 * node lost, etc). It is requeued (up to maxAttempts) or moved to the
 * dead-letter state - the same pattern SQS's visibility timeout + a DLQ
 * redrive policy implement, just running in-process here.
 */
@Component
public class JobRecoveryWatchdog {

    private static final Logger log = LoggerFactory.getLogger(JobRecoveryWatchdog.class);

    private final TranscodeJobRepository jobRepository;
    private final JobQueueService queueService;

    public JobRecoveryWatchdog(TranscodeJobRepository jobRepository, JobQueueService queueService) {
        this.jobRepository = jobRepository;
        this.queueService = queueService;
    }

    @Scheduled(fixedDelay = 3000)
    public void recoverStuckJobs() {
        Instant cutoff = Instant.now().minusSeconds(TranscodeWorkerTask.VISIBILITY_TIMEOUT_SECONDS);
        List<TranscodeJob> stuck = jobRepository.findByStatusAndPickedUpAtBefore(TranscodeJob.Status.PROCESSING, cutoff);

        for (TranscodeJob job : stuck) {
            int attempts = job.getAttempts() + 1;
            job.setAttempts(attempts);
            job.setLastError("Visibility timeout exceeded - presumed worker crash (" + job.getWorkerId() + ")");

            if (attempts < job.getMaxAttempts()) {
                job.setStatus(TranscodeJob.Status.QUEUED);
                jobRepository.save(job);
                queueService.enqueue(job.getId());
                log.warn("Watchdog recovered job {} from presumed-crashed worker {} (attempt {}/{}) - requeued",
                        job.getId(), job.getWorkerId(), attempts, job.getMaxAttempts());
            } else {
                job.setStatus(TranscodeJob.Status.DEAD_LETTER);
                jobRepository.save(job);
                log.error("Watchdog moved job {} to dead-letter after {} attempts (last worker: {})",
                        job.getId(), attempts, job.getWorkerId());
            }
        }
    }
}
