INSERT INTO roles (role_id, name) VALUES
    (1, 'ADMIN'),
    (2, 'USER'),
    (3, 'PREMIUM');

INSERT INTO categories (category_id, name) VALUES
    (1, 'Action'),
    (2, 'Comedy'),
    (3, 'Drama'),
    (4, 'Sci-Fi'),
    (5, 'Documentary');

INSERT INTO users (user_id, username, password, role_id) VALUES
    ('11111111-1111-1111-1111-111111111111', 'alice', 'password', 1),
    ('22222222-2222-2222-2222-222222222222', 'bob', 'password', 2),
    ('33333333-3333-3333-3333-333333333333', 'carol', 'password', 3);

INSERT INTO videos (name, video_length, size, video_url) VALUES
    ( 'The Last Frontier', 7420, 2147483648, '/videos/the-last-frontier.mp4'),
    ( 'Midnight Run', 5680, 1610612736, '/videos/midnight-run.mp4'),
    ( 'Quantum Drift', 8453, 3221225472, '/videos/quantum-drift.mp4'),
    ( 'Paper Planes', 4399, 1073741824, '/videos/paper-planes.mp4'),
    ( 'Deep Ocean: Secrets', 6112, 2684354560, '/videos/deep-ocean-secrets.mp4');

INSERT INTO video_categories (video_categories_id, category_id, video_id) VALUES
    (gen_random_uuid(), 1, 1),
    (gen_random_uuid(), 3, 1),
    (gen_random_uuid(), 2, 2),
    (gen_random_uuid(), 4, 3),
    (gen_random_uuid(), 3, 4),
    (gen_random_uuid(), 5, 5),
    (gen_random_uuid(), 1, 5);

INSERT INTO watch_history (watch_history_id, user_id, video_id, date) VALUES
    (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 1, now() - interval '2 days'),
    (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 3, now() - interval '1 days'),
    (gen_random_uuid(), '22222222-2222-2222-2222-222222222222', 2, now() - interval '5 hours'),
    (gen_random_uuid(), '22222222-2222-2222-2222-222222222222', 5, now() - interval '1 day'),
    (gen_random_uuid(), '33333333-3333-3333-3333-333333333333', 4, now() - interval '30 minutes'),
    (gen_random_uuid(), '33333333-3333-3333-3333-333333333333', 1, now() - interval '3 days');