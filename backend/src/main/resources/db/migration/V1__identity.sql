-- Usuários e sessões (refresh tokens)
create table users (
    id                    uuid primary key,
    name                  varchar(120) not null,
    email                 varchar(254) not null,
    password_hash         varchar(100) not null,
    role                  varchar(20)  not null check (role in ('ADMIN', 'SALES', 'VIEWER')),
    active                boolean      not null default true,
    failed_login_attempts integer      not null default 0,
    locked_until          timestamptz,
    last_login_at         timestamptz,
    version               bigint       not null default 0,
    created_at            timestamptz  not null,
    created_by            varchar(64),
    updated_at            timestamptz  not null,
    updated_by            varchar(64)
);

create unique index ux_users_email on users (email);

create table refresh_tokens (
    id         uuid primary key,
    user_id    uuid        not null references users (id),
    token_hash varchar(64) not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    revoked_at timestamptz
);

create unique index ux_refresh_tokens_hash on refresh_tokens (token_hash);
create index ix_refresh_tokens_user on refresh_tokens (user_id) where revoked_at is null;
