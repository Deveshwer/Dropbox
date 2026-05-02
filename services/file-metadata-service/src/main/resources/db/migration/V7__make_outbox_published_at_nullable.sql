alter table outbox_events
    alter column published_at drop not null;