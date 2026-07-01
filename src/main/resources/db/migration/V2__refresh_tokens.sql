-- Opaque, rotating refresh tokens (only the SHA-256 hash is stored)

create table refresh_tokens (
    id         uuid primary key,
    user_id    uuid        not null,
    token_hash varchar(64) not null unique,
    expires_at timestamp(6) with time zone not null,
    revoked    boolean     not null,
    created_at timestamp(6) with time zone not null
);

create index idx_refresh_tokens_user on refresh_tokens (user_id);
