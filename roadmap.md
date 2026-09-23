# Roadmap — streamEra

**Vision:** A streaming platform backend. Users register, browse catalogs, stream real HLS video, and track watch history.
**Architecture:** A strictly separated modular monolith, evolving into a Monolith + 1 Worker architecture for real video transcoding.

## Phase 1 — Core Monolith & Data Foundations
- [x] Set up database migrations (Flyway) to create the core `Users`, `Catalog`, and `WatchHistory` tables.
- [x] Build the basic entity classes (`User`, `Video`, `WatchRecord`).
- [x] Enforce domain boundaries: ensure no direct database joins exist between distinct domains (use primitive ID references instead).

## Phase 2 — The GraphQL Edge & Security
- [ ] Integrate Netflix DGS to serve data via a GraphQL API.
- [ ] Define the GraphQL schema to support querying the video catalog, user profiles, and watch history.
- [ ] Implement JWT authentication and authorization.
- [ ] Update GraphQL DataFetchers to require a valid JWT to query videos.
- [ ] Implement a GraphQL Mutation (`updateWatchProgress`) that the video player will ping to save the user's current timestamp.

## Phase 3 — Real Video Processing & Playback (The Streaming Engine)
- [ ] Install FFmpeg on your local development machine.
- [ ] Write Java logic using `ProcessBuilder` to execute an FFmpeg command that slices raw `.mp4` files into `.ts` chunks with an `.m3u8` playlist.
- [ ] Expose a dedicated HTTP endpoint in the monolith to serve generated static `.m3u8` and `.ts` files directly over standard HTTP.
- [ ] Create a basic `index.html` file using `hls.js` to physically test browser playback and verify the GraphQL mutation ping.

## Phase 4 — Distributed Worker Extraction (Message Queue)
- [ ] Configure `docker-compose` to run RabbitMQ alongside Postgres.
- [ ] Build a video upload endpoint in the monolith that publishes a "Video Uploaded" event to RabbitMQ instead of processing the file synchronously.
- [ ] Extract the Java FFmpeg logic from Phase 3 into a standalone Worker service.
- [ ] The Worker listens to the queue, executes the HLS transcoding, saves the files, and updates the video status in the database to "Ready".

## Phase 5 — Polish & Verification
- [ ] Write integration tests (using Testcontainers) for the message broker.
- [ ] Ensure the build is strictly green (`mvn test` passes consistently) and set up a basic CI pipeline.
- [ ] Write a comprehensive `README.md` detailing startup instructions, how to test video playback, and the architectural reasoning.