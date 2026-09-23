package com.sudesh.transcodequeue.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * In-memory stand-in for a message queue (AWS SQS / RabbitMQ in production).
 * Workers block on {@link #take(long, TimeUnit)} the same way they would poll
 * SQS or consume from a RabbitMQ channel - swapping the implementation for a
 * real broker later does not require changing any worker logic.
 */
@Service
public class JobQueueService {

    private final LinkedBlockingQueue<Long> queue = new LinkedBlockingQueue<>();

    public void enqueue(Long jobId) {
        queue.offer(jobId);
    }

    /** Blocks up to the timeout waiting for a job id; returns null on timeout (mirrors SQS long-polling). */
    public Long take(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public int size() {
        return queue.size();
    }
}
