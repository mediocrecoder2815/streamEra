CREATE TABLE roles(
    role_id smallserial primary key,
    name text not null unique
);
CREATE TABLE videos(
    video_id BIGSERIAL primary key,
    name TEXT unique not null,
    video_length int not null,
    size bigint not null,
    video_url TEXT NOT NULL
);
CREATE TABLE users(
    user_id UUID PRIMARY KEY,
    username varchar(42) not null unique,
    password text not null,
    role_id smallint not null references roles(role_id)
);
create table categories(
    category_id SERIAL Primary Key,
    name text not null unique
);
CREATE TABLE watch_history(
    watch_history_id UUID primary key,
    user_id UUID not null,
    video_id BIGINT not null,
    date timestamptz not null default now()
);
create table video_categories(
    video_categories_id uuid primary key,
    category_id int not null references categories(category_id),
    video_id bigint not null references videos(video_id),
    unique (category_id, video_id)
);