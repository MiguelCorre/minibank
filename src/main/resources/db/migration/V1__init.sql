-- minibank initial schema (matches the JPA entities; ddl-auto is 'validate')

create table accounts (
    id             uuid primary key,
    account_number varchar(34)  not null unique,
    holder_name    varchar(255) not null,
    currency       varchar(3)   not null,
    balance        numeric(19, 2) not null,
    version        bigint       not null,
    created_at     timestamp(6) with time zone not null
);

create table transfers (
    id              uuid primary key,
    idempotency_key varchar(128) not null unique,
    from_account_id uuid         not null,
    to_account_id   uuid         not null,
    amount          numeric(19, 2) not null,
    currency        varchar(3)   not null,
    description     varchar(140),
    created_at      timestamp(6) with time zone not null
);

create table ledger_entries (
    id            uuid primary key,
    account_id    uuid not null,
    transfer_id   uuid,
    type          varchar(6) not null check (type in ('DEBIT', 'CREDIT')),
    amount        numeric(19, 2) not null,
    balance_after numeric(19, 2) not null,
    created_at    timestamp(6) with time zone not null
);

create index idx_ledger_entries_account_created on ledger_entries (account_id, created_at);

create table users (
    id            uuid primary key,
    email         varchar(255) not null unique,
    password_hash varchar(255) not null,
    display_name  varchar(255) not null,
    created_at    timestamp(6) with time zone not null
);
