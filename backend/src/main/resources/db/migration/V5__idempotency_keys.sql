-- Idempotency-Key dos POSTs críticos (pedidos, importação). Linhas antigas podem ser apagadas por job (Fase 5).
create table idempotency_keys (
    scope       varchar(40)  not null,
    key         varchar(100) not null,
    user_id     uuid         not null,
    resource_id uuid         not null,
    created_at  timestamptz  not null,
    primary key (scope, key, user_id)
);
