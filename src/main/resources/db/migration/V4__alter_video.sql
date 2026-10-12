ALTER TABLE videos
    ADD COLUMN video_status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        CHECK (video_status IN ('PENDING', 'OK', 'ERROR', 'DELETED'));