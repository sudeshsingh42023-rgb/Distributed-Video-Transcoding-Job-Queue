package com.sudesh.transcodequeue.repository;

import com.sudesh.transcodequeue.model.TranscodeJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TranscodeJobRepository extends JpaRepository<TranscodeJob, Long> {
    List<TranscodeJob> findByStatus(TranscodeJob.Status status);
    List<TranscodeJob> findByStatusAndPickedUpAtBefore(TranscodeJob.Status status, Instant cutoff);
}
