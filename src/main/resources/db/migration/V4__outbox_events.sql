-- Transactional outbox: domain events are stored in the same transaction as
-- the state change; a relay publishes them asynchronously.

create table outbox_events (
    id           uuid primary key,
    event_type   varchar(100) not null,
    aggregate_id uuid         not null,
    payload      text         not null,
    created_at   timestamp(6) with time zone not null,
    published_at timestamp(6) with time zone
);

create index idx_outbox_unpublished on outbox_events (created_at) where published_at is null;
