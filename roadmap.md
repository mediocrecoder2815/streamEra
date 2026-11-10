# Roadmap — streamEra

**Vision:** A streaming platform backend. Users register, browse catalogs, upload videos, stream real HLS video, and track watch history.
**Architecture:** A strictly separated modular monolith, evolving into a Monolith + 1 Worker architecture for real video transcoding.

**Demo in one sentence:** *A user uploads a video, watches it move from `PENDING` to `OK`, then plays it and resumes where they left off.*

> **How to use this roadmap**
> One milestone at a time. Do not start the next milestone until the current one is demoable. The hard part is not writing code — it is keeping ordering and state correct. If a milestone takes more than a few days, split it again.

## Current state

- [x] Auth, catalog queries, watch history, progress mutation, JWT.
- [x] FFmpeg installed; RabbitMQ in `docker-compose`; monolith + worker config.
- [x] Worker skeleton: consumes, downloads, runs ffmpeg, uploads HLS.
- [x] Demo frontend: catalog, player, login/register, watch history.
- [ ] Upload handshake (presigned URL + confirm).
- [ ] Closed status loop (`PENDING → PROCESSING → OK/ERROR`).
- [ ] Real browser upload; seeded titles currently point at a bundled `/media/demo.mp4` placeholder.

## Invariants — never break these

1. **Never enqueue processing before the source object exists.**
2. **The worker never reports success before its uploads finish.**
3. **Status moves one way:** `PENDING → PROCESSING → OK/ERROR`; duplicate events are no-ops; `DELETED` never resurrects.
4. **The worker is stateless; the monolith owns the database.**
5. **Events are facts, not commands:** `video.processed { videoId, playlistKey }`, not “please set status OK”.
6. **`OK` means “playlist and segments are readable,”** not “a message arrived”.

---

## Phase 1 — Core Monolith & Data Foundations
- [x] Set up database migrations (Flyway) to create the core `Users`, `Catalog`, and `WatchHistory` tables.
- [x] Build the basic entity classes (`User`, `Video`, `WatchRecord`).
- [x] Enforce domain boundaries: ensure no direct database joins exist between distinct domains (use primitive ID references instead).

## Phase 2 — The GraphQL Edge & Security
- [x] Integrate Spring for GraphQL (spring-graphql) to serve data via a GraphQL API.
- [x] Define the GraphQL schema (`schema.graphqls`) to support querying the video catalog, user profiles, and watch history.
- [x] Implement JWT authentication and authorization.
- [x] Update GraphQL controller `@QueryMapping` methods to require a valid JWT to query videos.
- [x] Implement a GraphQL Mutation (`update_watch_historyProgress`) that the video player will ping to save the user's current timestamp.

---

## Phase 3 — The Walking Skeleton (close the loop)

**Goal:** one real video goes from upload to playable HLS. Happy path only.
**Definition of done:** a logged-in user picks an MP4 in the browser, sees upload progress, waits through `PROCESSING`, sees `OK`, and plays the HLS stream.

> **Do not add yet:** CDN, S3 event notifications, multipart uploads, GraphQL subscriptions, multiple workers, DRM. Add them only when you feel the specific pain they solve.

### 3.0 Design the contract — paper only

- [x] Write the status machine and what each state means:
  - `PENDING` — row exists, no source object yet.
  - `PROCESSING` — source verified, worker is handling it.
  - `OK` — playlist and segments uploaded and readable.
  - `ERROR` — upload or processing failed.
  - `DELETED` — never processed again.
- [x] Write the events:
  - `video.uploaded { videoId, sourceKey }`
  - `video.processed { videoId, playlistKey }`
  - `video.failed { videoId, reason }`
- [x] Write the invariants above.
- [x] Decide ownership: monolith owns the DB; worker is stateless.
- [x] Note: `PROCESSING` needs a DB migration because the current check constraint only allows `PENDING`, `OK`, `ERROR`, `DELETED`.

**Done when:** you can explain the happy path and every transition without looking at code.

**Learn:** state machines, event contracts, ownership boundaries.

### 3.1 Object storage — MinIO

- [x] Add MinIO to `docker-compose` (API port + web console).
- [x] Create a bucket.
- [x] Decide key layout: `raw/{videoId}/source.{ext}` and `hls/{videoId}/...`.
- [x] Upload a file manually; read it back; inspect size and content type.
- [x] Point the app's storage client at MinIO through config (endpoint, credentials, bucket).

**Done when:** the same file is visible in the MinIO console and readable by the app.

**Learn:** buckets, keys, endpoints, object metadata, why storage is not a database.

> If MinIO blocks you: use a local `media/` folder and a backend upload endpoint for 3.2–3.4, then swap in MinIO later. The state machine is the point; storage is replaceable.

### 3.2 Upload handshake — the ordering gate

- [x] Remove the immediate publish from `addVideo`; right now it enqueues before any upload can happen.
- [x] Mutation creates a `PENDING` row and returns a **presigned PUT URL**; it does **not** enqueue anything.
- [ ] Upload the file directly to MinIO with `curl` first, not the browser.
- [x] Add a confirm mutation that `HEAD`s the object, checks size/format, sets `PROCESSING`, and publishes `video.uploaded`.
- [x] Reject missing, zero-byte, or wrong-type objects before enqueueing.
- [x] Configure MinIO CORS for the browser origin when you move to the frontend.

**Done when:** an object exists in `raw/`, the row is `PROCESSING`, and one `video.uploaded` message is visible in RabbitMQ.

**Learn:** presigned URLs, `HEAD`, CORS, verifying facts instead of trusting the client.

### 3.3 Worker — do the slow work

- [x] Worker consumes the upload event, downloads the raw object, and runs ffmpeg.
- [x] Worker uploads the generated HLS files back to storage.
- [x] Verify the output directory contains `index.m3u8` and at least one `.ts` segment before publishing anything.
- [ ] Upload segments first, playlist last, so a visible playlist never points at missing segments.
- [ ] Replace the plain string message with a structured success event: `video.processed { videoId, playlistKey }`.
- [ ] Publish `video.failed { videoId, reason }` on every processing error.
- [ ] Make the worker safe to run twice for the same video (overwrite is fine; state must not corrupt).

**Done when:** one message produces a complete playlist + segments in MinIO and exactly one success event.

**Learn:** idempotent consumption, temp files, ffmpeg exit codes, output verification.

### 3.4 Status integration — close the loop

- [x] A consumer exists (`receiveMessage`), but it only flips status and never sets `videoUrl`.
- [ ] Monolith consumes `video.processed` and `video.failed`.
- [ ] On success: set `OK` and `videoUrl = {mediaBaseUrl}/hls/{videoId}/index.m3u8`.
- [ ] On failure: set `ERROR` and store the reason (log or column).
- [ ] Ignore duplicate or stale events; never move out of `DELETED`.
- [ ] Decide whether to trust the event or `HEAD` the playlist before setting `OK`.

**Done when:** uploading one file moves the row to `OK` or `ERROR` with no manual SQL.

**Learn:** transitions, idempotency, publish ordering, partial failure.

### 3.5 Frontend — make it visible

- [ ] Upload modal: title, file picker, progress bar (use XHR for upload progress).
- [ ] Two-step flow: request upload ticket → PUT to MinIO → confirm.
- [ ] Catalog shows status badges: `PENDING`, `PROCESSING`, `OK`, `ERROR`.
- [ ] Poll `video_byId` while processing; switch to the player when `OK`.
- [ ] Play the real HLS playlist with hls.js and save progress as before.

**Done when:** the one-sentence demo works with a real file, not `/media/demo.mp4`.

**Learn:** asynchronous UX, polling, honest error messages.

---

## Phase 4 — Failure, Consistency & Correctness

**Goal:** the pipeline survives restarts, duplicates, retries, and abandoned uploads.
**Definition of done:** you can kill any component mid-flight and still end in a consistent state.

- [ ] Enable publisher confirms and durable queues.
- [ ] Manual ack / retry policy; don't spin forever on a poison message.
- [ ] Dead-letter queue for events that repeatedly fail.
- [ ] Idempotent handlers: the same event twice is a no-op.
- [ ] Every failure ends in `ERROR`; nothing gets stuck in `PROCESSING` forever.
- [ ] Reconciliation job:
  - `PENDING` older than N minutes → clean up or mark `ERROR`.
  - `PROCESSING` older than N minutes → re-enqueue or mark `ERROR`.
- [ ] Upload expiry: abandon an upload and confirm the row does not leak.
- [ ] Event versioning: every message carries a type and version; unknown versions are rejected safely.
- [ ] Validate outputs: playlist exists, segments exist, no zero-byte objects.
- [ ] Tests: state transitions, worker idempotency, message contract.

**Learn:** at-least-once delivery, idempotency, reconciliation, why “exactly once” is mostly a story.

---

## Phase 5 — Deployment & Delivery (recommended for the portfolio)

- [ ] Move from MinIO to real S3, Cloudflare R2, or Backblaze B2 via config only.
- [ ] Bucket CORS and private access; decide public vs signed playback.
- [ ] Deploy monolith, worker, Postgres, and RabbitMQ.
- [ ] Set `mediaBaseUrl` correctly for the deployed environment.
- [ ] Verify upload → processing → playback on the deployed URL.
- [ ] `README.md`: architecture diagram, tradeoffs, startup instructions, known limits.

**Learn:** config over rewrite, CORS in the real world, deployment failure modes.

---

## Phase 6 — Polish & Verification

- [ ] Testcontainers integration test for broker + storage.
- [ ] `mvn test` green and a basic CI pipeline.
- [ ] Structured logs: `videoId`, duration, failure reason.
- [ ] Optional: GraphQL subscriptions instead of polling.
- [ ] Optional: CDN / CloudFront for delivery.
- [ ] Optional: multiple workers and a per-video lock.
- [ ] Optional: signed playback URLs.

---

## Deliberately deferred

These are not bad ideas. They are distractions until the loop above works:
CDN, S3 event notifications, multipart uploads, transactional outbox, GraphQL subscriptions, DRM, multi-worker scaling, Kubernetes, analytics.
