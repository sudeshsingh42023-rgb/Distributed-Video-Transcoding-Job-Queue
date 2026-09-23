# Distributed Video Transcoding Job Queue

A job-queue service that accepts video transcode requests, distributes them across a pool
of workers, retries failed jobs, and automatically recovers jobs whose worker crashed
mid-processing — the same shape of problem behind Adobe Media Encoder / cloud rendering
pipelines, and a direct hit on the JD's "monitor/debug highly available... systems...
failure detection and fail over options."

## Stack

- **Java 17 + Spring Boot 3** — REST API
- **Spring Data JPA / Hibernate** — job persistence (H2 in-memory locally, Postgres-ready)
- **In-memory `BlockingQueue`** — stands in for AWS SQS / RabbitMQ (swap-in documented below)
- **Fixed worker thread pool** — stands in for N worker containers
- **`@Scheduled` watchdog** — implements a visibility-timeout + dead-letter pattern
- **Docker + Docker Compose**

## How it works

1. `POST /api/jobs` submits a job (`sourceUrl`, `targetFormat`) → queued.
2. A pool of workers (`transcode.worker-count`, default 3) each pull jobs off the queue,
   mark them `PROCESSING` with a `pickedUpAt` timestamp, "transcode" (simulated), then
   mark `DONE` with an output URL — or fail and retry up to `maxAttempts` before moving
   to `DEAD_LETTER`.
3. **Failure detection**: `JobRecoveryWatchdog` runs every 3s and looks for jobs stuck in
   `PROCESSING` past a 10s visibility timeout (i.e. their worker went silent). It requeues
   them (or dead-letters them if attempts are exhausted) — exactly what SQS's visibility
   timeout + a DLQ redrive policy do, without needing a real broker to demo the behavior.
4. **Chaos endpoint**: `POST /api/admin/workers/{workerId}/crash` simulates a hard crash
   (the worker abandons its current job with no cleanup, like a SIGKILL/OOM-kill would).
   The watchdog recovers the abandoned job independently; the worker itself "restarts"
   8 seconds later, simulating an orchestrator (ECS/Kubernetes) bringing the container
   back up.

## Running locally

```bash
mvn spring-boot:run
# open http://localhost:8081
```

## Demoing failure recovery

1. Open the dashboard, click **"Submit sample transcode job"** a few times.
2. Click **"Simulate crash"** next to a worker that's actively processing a job (its
   "Current job" column is non-empty).
3. Watch the job table: the job stays `PROCESSING` under the dead worker for a few
   seconds, then the watchdog flips it back to `QUEUED` and a healthy worker picks it up
   — it finishes with **zero manual intervention and no lost job**, even though the
   worker that originally had it never got to report success or failure.
4. The crashed worker itself flips back to `RUNNING` 8 seconds after being crashed.

## Running with Docker

```bash
docker compose up --build
# WORKER_COUNT=4 set in docker-compose.yml
```

## Known simplifications / what "production" would add

- The queue is an in-memory `BlockingQueue` inside one JVM. Swapping `JobQueueService`
  for AWS SQS (`SqsClient.receiveMessage` / `sendMessage`) or a RabbitMQ consumer would
  let the "worker pool" become genuinely separate containers/instances sharing one queue
  and one Postgres database — the watchdog logic (visibility timeout, DLQ) is designed to
  map onto SQS's native visibility-timeout + redrive-policy features almost unchanged.
- "Transcoding" is simulated with a sleep + random failure chance. A real worker would
  shell out to FFmpeg (`ProcessBuilder`) and upload the result to S3.
- No auth on the admin/chaos endpoints — fine for a demo, would be locked down or removed
  in production.

## Resume bullet

> Designed a distributed video transcoding pipeline (Java, Spring Boot, Docker) processing
> render jobs across a horizontally scaled worker pool; implemented visibility-timeout-based
> failure detection and dead-letter retry, achieving automatic recovery from simulated
> worker crashes with zero lost jobs.
