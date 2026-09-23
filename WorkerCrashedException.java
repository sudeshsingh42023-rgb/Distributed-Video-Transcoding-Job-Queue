package com.sudesh.transcodequeue.service;

/**
 * Thrown internally when a worker has been flagged as "crashed" (via the
 * chaos/admin endpoint) mid-job. The job is deliberately left un-updated in
 * the DB - exactly what happens on a real hard crash (SIGKILL, OOM, node
 * failure) - so that job recovery relies entirely on the watchdog's
 * visibility-timeout check, not on the worker performing any cleanup.
 */
public class WorkerCrashedException extends RuntimeException {
    public WorkerCrashedException(String workerId) {
        super("Worker " + workerId + " crashed mid-job");
    }
}
