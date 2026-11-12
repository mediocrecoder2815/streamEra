-- Remove the bundled demo catalog.
--
-- The seeded rows from V3 were rewritten to point at /media/demo.mp4 by V7.
-- Uploaded and processed videos are now the only playable titles, and the
-- frontend no longer falls back to the bundled sample clip.

-- Dependent rows first (video_categories has an FK, watch_history does not).
DELETE FROM watch_history
WHERE video_id IN (SELECT video_id FROM videos WHERE video_url = '/media/demo.mp4');

DELETE FROM video_categories
WHERE video_id IN (SELECT video_id FROM videos WHERE video_url = '/media/demo.mp4');

DELETE FROM videos
WHERE video_url = '/media/demo.mp4';
