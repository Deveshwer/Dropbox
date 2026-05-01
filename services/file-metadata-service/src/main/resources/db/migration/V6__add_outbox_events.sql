create table outbox_events (
    id uuid primary key,
    event_type varchar(255) not null,
    resource_type varchar(255) not null,
    resource_id uuid not null,
    actor_id uuid not null,
    payload text not null,
    created_at timestamp not null,
    published_at timestamp not null
);