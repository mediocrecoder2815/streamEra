# Roadmap — streamEra

**Goal:** finish one credible, demoable project.

**Not the goal:** build a production-grade reliability platform in this repository.

**Demo in one sentence:** a signed-in user uploads a real MP4, attaches categories, watches it move from `PENDING` to `OK`, plays the HLS stream, and resumes where they left off.

**Failure demo:** stop the worker after `confirm`; the video must not stay `PROCESSING` forever.

> **Scope rule**
> - If the failure cannot be reproduced locally, it does not belong in Phase 1 or Phase 2.
> - A demonstrable failure is worth more than a theoretical guard.
> - Complexity is not a feature by itself.

---

## Current state

- [x] Auth, catalog, watch history, progress mutation, JWT.
- [x] Monolith + worker architecture.
- [x] RabbitMQ, MinIO, FFmpeg, Docker Compose.
- [x] Presigned upload URL + confirm step.
- [x] Worker consumes upload events, downloads the source, runs FFmpeg, uploads HLS.
- [x] Status state machine: `PENDING → PROCESSING → OK/ERROR`; `DELETED` is terminal.
- [x] Real browser upload and real HLS playback.
- [x] Frontend upload categories via `categories` query + `update_video`.
- [x] Publisher confirms/returns exist on upload and worker events.
- [ ] Test compilation and test suite are not green.
- [ ] No watchdog for stale `PROCESSING` rows.
- [ ] Categories have two backend paths: `add_video` and `update_video`; keep one.
- [ ] README and repeatable startup instructions are incomplete.

### Frozen, not a milestone

Publisher confirms/returns are already present. Do not extend that work. They are defensive guards for routing/config failures that do not occur in the local demo. Keep them working; do not build more reliability around them unless a real failure appears.

---

## Finish line

From a clean database:

1. Start the stack.
2. Sign in.
3. Upload one MP4 with categories.
4. Watch it become `OK`.
5. Play the HLS stream and resume after reload.
6. Stop the worker mid-flight and confirm the video eventually becomes `ERROR` instead of staying `PROCESSING`.
7. Run the test suite.
8. Follow the README without asking for missing steps.

---

## Phase 1 — Real failures only (must)

**Goal:** close gaps you can actually reproduce.

### 1. Stale `PROCESSING` watchdog

- [ ] Add `status_updated_at` to the video record.
- [ ] Update it on every status change.
- [ ] Add a scheduled check for rows stuck in `PROCESSING` longer than a safe window.
- [ ] Mark timed-out rows `ERROR`.
- [ ] Do not republish automatically.
- [ ] Verify by stopping the worker after `confirm`.

This is the only new reliability feature required.

### 2. Make the failure path visible

- [ ] Upload a corrupt/invalid MP4 and verify the worker emits `failed`.
- [ ] Verify the video becomes `ERROR` and the UI shows a failure state.
- [ ] Verify a stopped worker produces a timeout error through the watchdog.

### 3. One category path

- [ ] Keep frontend category attachment through `update_video`.
- [ ] Remove or fix the unused `add_video` category path.
- [ ] Do not maintain two ways to attach categories.

### 4. Tests

- [ ] Fix the current test compilation break.
- [ ] Test status transitions, including duplicate and stale events.
- [ ] Test the watchdog timeout rule.
- [ ] Keep the suite small and relevant.

**Done when:** the happy path and the two real failure paths are demonstrable.

---

## Phase 2 — Portfolio packaging (must)

**Goal:** someone else can understand and run the project.

- [ ] `README.md`: what it does, architecture diagram, startup steps, tradeoffs, known limits.
- [ ] One-command local startup with Docker Compose.
- [ ] Logs include `videoId`, status, and failure reason.
- [ ] Verify mutations require authentication.
- [ ] Verify no secrets are committed.
- [ ] `mvn test` is green.
- [ ] Basic CI pipeline.
- [ ] Document the failure demo: stopped worker → watchdog → `ERROR`.

**Done when:** a reviewer can run the demo from the README without asking for missing steps.

---

## Phase 3 — Stretch (optional)

Only after the finish line:

- [ ] Signed playback URLs.
- [ ] CDN for HLS delivery.
- [ ] GraphQL subscriptions instead of polling.
- [ ] Multiple workers with a per-video claim.
- [ ] Richer metrics and dashboards.

---

## Deferred / not needed

These are valid engineering topics, but they are not failures this demo produces:

- Publisher-return expansion beyond the current guard.
- Consumer retry/backoff tuning.
- Dead-letter queues for poison messages.
- Transactional outbox.
- Multi-worker scaling and distributed locks.
- Cache layer and cache invalidation.
- Elasticsearch / external search.
- Full observability stack.
- S3 event notifications.
- Multipart uploads.
- DRM.
- Kubernetes.
- Analytics.
- Advanced security hardening beyond the minimum.

Pick these for a different project. Do not add them here just because they are “correct” in production.

---

## Progress rules

- Progress is a demoable milestone, not the number of technologies in the repository.
- If you cannot reproduce the failure, do not build the guard.
- If a problem does not threaten the demo or a finish-line invariant, document it and move on.
- Stop at “credible portfolio project,” not at “production platform.”
- A finished smaller system teaches more than an unfinished larger one.
