-- Demo-only data fixes that make the sample frontend usable on a fresh database.

-- 1. The V3 mock rows were inserted before the video_status column existed, so
--    they defaulted to PENDING. PlaybackService only returns OK videos, which
--    made every seeded title unplayable. Mark the seeded rows ready and point
--    them at the bundled sample clip in static/media.
UPDATE videos
SET video_status = 'OK'
WHERE video_status = 'PENDING'
  AND video_url LIKE '/videos/%';

UPDATE videos
SET video_url = '/media/demo.mp4'
WHERE video_url LIKE '/videos/%';

-- 2. The V3 mock users store the plaintext password "password", but AuthService
--    verifies with BCryptPasswordEncoder, so the seeded accounts could not log
--    in. Replace it with a BCrypt hash of "password" for the seeded UUIDs only.
UPDATE users
SET password = '$2b$10$FCnDdubf0vsXLplgPImcmuaIEmzH4WnEDKlbC9h6D/M2PmWxz7DI6'
WHERE user_id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333'
);
