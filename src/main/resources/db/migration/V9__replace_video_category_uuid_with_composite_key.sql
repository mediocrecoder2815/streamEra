-- Replace the surrogate UUID key with the natural composite key expected by
-- the Hibernate @ManyToMany mapping. Hibernate only writes video_id and
-- category_id, so the unused UUID column must not remain required.

ALTER TABLE video_categories
    DROP CONSTRAINT IF EXISTS video_categories_pkey;

ALTER TABLE video_categories
    DROP CONSTRAINT IF EXISTS video_categories_category_id_video_id_key;

ALTER TABLE video_categories
    DROP COLUMN IF EXISTS video_categories_id;

ALTER TABLE video_categories
    ADD CONSTRAINT video_categories_pkey PRIMARY KEY (video_id, category_id);
