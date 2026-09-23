package com.sudesh.transcodequeue.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "transcode_jobs")
public class TranscodeJob {

    public enum Status { QUEUED, PROCESSING, DONE, FAILED_RETRYING, DEAD_LETTER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sourceUrl;      // e.g. s3://raw-uploads/clip123.mov

    @Column(nullable = false)
    private String targetFormat;   // e.g. "mp4_1080p", "webm_720p"

    @Enumerated(EnumType.STRING)
    private Status status = Status.QUEUED;

    private int attempts = 0;
    private int maxAttempts = 3;

    private String workerId;       // which worker currently/last owns this job
    private Instant pickedUpAt;    // when the current attempt started (visibility-timeout anchor)

    private String outputUrl;      // set on success
    private String lastError;      // set on failure

    private final Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public TranscodeJob() {}

    public TranscodeJob(String sourceUrl, String targetFormat) {
        this.sourceUrl = sourceUrl;
        this.targetFormat = targetFormat;
    }

    public void touch() { this.updatedAt = Instant.now(); }

    // --- getters/setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getTargetFormat() { return targetFormat; }
    public void setTargetFormat(String targetFormat) { this.targetFormat = targetFormat; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; touch(); }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }

    public Instant getPickedUpAt() { return pickedUpAt; }
    public void setPickedUpAt(Instant pickedUpAt) { this.pickedUpAt = pickedUpAt; }

    public String getOutputUrl() { return outputUrl; }
    public void setOutputUrl(String outputUrl) { this.outputUrl = outputUrl; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
