-- 1. Rename the column
ALTER TABLE watch_history
    RENAME COLUMN timestamp TO playback_position_seconds;

-- 2. Drop the existing string default value
ALTER TABLE watch_history
    ALTER COLUMN playback_position_seconds DROP DEFAULT;

-- 3. Change the column type to bigint (converting existing data to 0)
ALTER TABLE watch_history
    ALTER COLUMN playback_position_seconds TYPE bigint USING 0;

-- 4. Set the new numeric default
ALTER TABLE watch_history
    ALTER COLUMN playback_position_seconds SET DEFAULT 0;