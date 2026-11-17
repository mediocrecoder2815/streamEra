# Roadmap — streamEra

**Goal:** finish one credible, demoable project.

**Not the goal:** build a production-grade reliability platform in this repository.

**Demo in one sentence:** a user uploads a video, watches it move from `PENDING` to `OK`, plays the real HLS stream, and resumes where they left off.

> **Scope rule**
> - **Finish** = required to call this project done.
> - **Stretch** = only after the finish line.
> - **Next project** = deliberately out of scope here.
>
> Complexity is not a feature by itself. The point of this roadmap is to stop the project from expanding faster than it can be finished.

---

## Current state

- [x] Auth, catalog, watch history, progress mutation, JWT.
- [x] Monolith + worker architecture.
- [x] RabbitMQ, MinIO, FFmpeg, Docker Compose.
- [x] Presigned upload URL + confirm step.
- [x] Worker consumes upload events, downloads the source, runs ffmpeg, uploads HLS.
- [x] Status loop exists: `PENDING → PROCESSING → OK/ERROR`.
- [x] Real browser upload and real HLS playback.
- [x] Demo fallback catalog removed.
- [x] Frontend upload, catalog, player, and watch-history resume.
- [ ] Reliability layer is incomplete: durable queues exist, but publisher confirms, retry/DLQ, idempotency, and the watchdog are not all in place.
- [ ] Known gaps: worker idempotency, playlist upload ordering, duplicate/stale event guards, catalog status filtering, and stale-`PROCESSING` recovery.

## Target invariants

These are the rules the finished version should enforce:

1. Never enqueue processing before the source object exists.
2. The worker never reports success before its uploads finish.
3. Status moves forward: `PENDING → PROCESSING → OK/ERROR`; duplicate events are no-ops; `DELETED` never resurrects.
4. The worker is stateless; the monolith owns the database.
5. Events are facts, not commands: `video.processed { videoId, playlistKey }`, not “please set status OK”.
6. `OK` means “playlist and segments are readable,” not “a message arrived”.

---

## Phase 1 — Finish the happy path (must)

**Goal:** one real video works end to end without manual SQL.
**Definition of done:** a clean database, one browser upload, `PENDING → PROCESSING → OK`, playable HLS, resume works.

- [ ] Worker idempotency: processing the same video twice must not corrupt state or output.
  - Use deterministic storage keys.
  - Overwrite existing HLS objects.
  - Clean up temp directories.
- [ ] Upload HLS segments first, playlist last.
  - A visible `index.m3u8` must never point at missing segments.
- [ ] Guard duplicate and stale events.
  - Duplicate `processed` / `failed` events should be no-ops.
  - `DELETED` must not resurrect.
  - Terminal states should not silently move backward.
- [ ] Make catalog visibility honest.
  - Either return all relevant statuses, or document that the catalog only shows `OK`/`PENDING`.
- [ ] Run one end-to-end acceptance test with a real file.
  - No demo fallback.
  - No manual database updates.
- [ ] Verify playback and resume after a page reload.

**Done when:** the one-sentence demo works from a clean setup.

**Learn:** idempotency, upload ordering, state transitions, end-to-end verification.

---

## Phase 2 — Minimum reliability (must, but bounded)

**Goal:** the pipeline does not silently lose work or stay stuck forever.
**Definition of done:** kill the worker mid-flight, replay an event, and leave a stale row; the system eventually converges to a consistent state.

- [ ] Publisher confirms for upload, processed, and failed events.
  - Enable correlated confirms and mandatory returns.
  - Treat nack and unroutable messages as failures, not success.
- [ ] Consumer failure policy.
  - Keep acknowledgement tied to successful completion.
  - Bounded retry with backoff.
  - Dead-letter queue for messages that exhaust retries.
  - Do not requeue poison messages forever.
- [ ] Watchdog for stale `PROCESSING` rows.
  - Add a status timestamp.
  - Detect rows older than a safe processing window.
  - Republish a limited number of times, then mark `ERROR`.
- [ ] Idempotent event handling in the monolith.
  - The same result event twice should not change the final outcome.
- [ ] Tests for the important rules.
  - Status transitions.
  - Worker idempotency.
  - Message contract.
  - Duplicate event handling.

**Done when:** a killed worker, a duplicated event, and a stale `PROCESSING` row all end in a defined state.

**Learn:** at-least-once delivery, acknowledgement timing, retries, DLQ, reconciliation.

---

## Phase 3 — Portfolio packaging (must)

**Goal:** someone else can understand and run the project.

- [ ] `README.md`: what it does, architecture diagram, startup steps, tradeoffs, known limits.
- [ ] One-command local startup with Docker Compose.
- [ ] Basic structured logs containing `videoId`, state, duration, and failure reason.
- [ ] Verify mutations require authentication.
- [ ] Verify no secrets are committed.
- [ ] `mvn test` green.
- [ ] Basic CI pipeline.
- [ ] Optional but useful: Testcontainers test for RabbitMQ + storage.

**Done when:** a reviewer can run the demo from the README without asking you for missing steps.

---

## Phase 4 — Stretch (optional, only after the finish line)

Do these only if the project is already demoable and you still have time:

- [ ] Signed playback URLs.
- [ ] CDN for HLS delivery.
- [ ] GraphQL subscriptions instead of polling.
- [ ] Multiple workers with a per-video claim/lock.
- [ ] Richer metrics and dashboards.
- [ ] Event versioning.

---

## Next project — deliberately deferred

These are good topics. They are not required to finish this project, and adding them now would create new failure modes before the current ones are closed:

- Cache layer and cache invalidation.
- Elasticsearch / external search.
- Full observability stack.
- Multi-worker scaling and distributed locks.
- Transactional outbox.
- S3 event notifications.
- Multipart uploads.
- DRM.
- Kubernetes.
- Analytics.
- Advanced security hardening beyond the minimum.

Pick one or two of these for the next project. Do not try to learn all of them in one system.

---

## Progress rules

- Progress is a **demoable milestone**, not the number of technologies in the repository.
- Do not add new infrastructure during Phase 1 or Phase 2.
- If a milestone takes more than a few days, split it or cut it.
- If a problem does not threaten an invariant, document it and move on.
- Stop at “credible portfolio project,” not at “production platform.”
- A finished smaller system teaches more than an unfinished larger one.
