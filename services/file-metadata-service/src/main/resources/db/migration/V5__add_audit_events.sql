create table audit_events (
    id uuid primary key,
    event_type varchar(100) not null,
    resource_type varchar(50) not null,
    resource_id uuid not null,
    actor_id uuid not null,
    metadata text null,
    created_at timestamp not null,

    constraint fk_audit_events_actor
          foreign key (actor_id) references users(id)
);