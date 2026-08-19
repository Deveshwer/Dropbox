create table sync_events (
    cursor bigserial primary key,
    event_id uuid not null,
    user_id uuid not null,
    event_type varchar(100) not null,
    resource_type varchar(50) not null,
    resource_id uuid not null,
    actor_id uuid not null,
    metadata text,
    created_at timestamp not null
);

create index idx_sync_events_user_cursor on sync_events(user_id, cursor);
create index idx_sync_events_created_at on sync_events(created_at);
