package com.sudesh.transcodequeue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the distributed-style video transcoding job queue.
 *
 * Mirrors the "cloud rendering" problem behind Adobe Media Encoder / Video
 * Cloud render jobs: jobs are submitted, distributed across a worker pool,
 * retried on failure, and recovered automatically if the worker processing
 * them disappears mid-job.
 */
@SpringBootApplication
@EnableScheduling
public class TranscodeQueueApplication {
    public static void main(String[] args) {
        SpringApplication.run(TranscodeQueueApplication.class, args);
    }
}
