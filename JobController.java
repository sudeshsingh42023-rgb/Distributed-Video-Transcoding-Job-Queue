package com.sudesh.transcodequeue.controller;

import com.sudesh.transcodequeue.model.TranscodeJob;
import com.sudesh.transcodequeue.repository.TranscodeJobRepository;
import com.sudesh.transcodequeue.service.JobQueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final TranscodeJobRepository jobRepository;
    private final JobQueueService queueService;

    public JobController(TranscodeJobRepository jobRepository, JobQueueService queueService) {
        this.jobRepository = jobRepository;
        this.queueService = queueService;
    }

    @PostMapping
    public ResponseEntity<TranscodeJob> submitJob(@RequestBody Map<String, Object> body) {
        String sourceUrl = (String) body.getOrDefault("sourceUrl", "s3://raw-uploads/unknown.mov");
        String targetFormat = (String) body.getOrDefault("targetFormat", "mp4_1080p");

        TranscodeJob job = new TranscodeJob(sourceUrl, targetFormat);
        if (body.containsKey("maxAttempts")) {
            job.setMaxAttempts(((Number) body.get("maxAttempts")).intValue());
        }
        TranscodeJob saved = jobRepository.save(job);
        queueService.enqueue(saved.getId());
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public List<TranscodeJob> listJobs() {
        return jobRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TranscodeJob> getJob(@PathVariable Long id) {
        return jobRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/dead-letter")
    public List<TranscodeJob> deadLetterJobs() {
        return jobRepository.findByStatus(TranscodeJob.Status.DEAD_LETTER);
    }

    /** Manually re-queue a dead-lettered job, e.g. after fixing the source file. */
    @PostMapping("/{id}/requeue")
    public ResponseEntity<TranscodeJob> requeue(@PathVariable Long id) {
        return jobRepository.findById(id).map(job -> {
            job.setAttempts(0);
            job.setStatus(TranscodeJob.Status.QUEUED);
            job.setLastError(null);
            TranscodeJob saved = jobRepository.save(job);
            queueService.enqueue(saved.getId());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/queue-depth")
    public Map<String, Integer> queueDepth() {
        return Map.of("queued", queueService.size());
    }
}
