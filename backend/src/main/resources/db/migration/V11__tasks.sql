-- Tarefas (seção 5.6). Manuais nesta fase; os alertas da Fase 5 usam origin = 'ALERTA' e alert_key.
create table tasks (
    id           uuid primary key,
    title        varchar(200)  not null,
    notes        varchar(2000),
    due_at       timestamptz   not null,
    owner_id     uuid references users (id),
    -- Negociação apagada só pode ter tarefas concluídas, que perdem o vínculo
    deal_id      uuid references deals (id) on delete set null,
    tender_id    uuid references tenders (id),
    origin       varchar(10)   not null check (origin in ('MANUAL', 'ALERTA')),
    -- O mesmo alerta (contrato + regra + período) nunca gera duas tarefas
    alert_key    varchar(120)  unique,
    completed_at timestamptz,
    completed_by uuid references users (id),
    version      bigint        not null default 0,
    created_at   timestamptz   not null,
    created_by   varchar(64),
    updated_at   timestamptz   not null,
    updated_by   varchar(64),
    constraint ck_tasks_alert_key check ((origin = 'ALERTA') = (alert_key is not null))
);

-- A lista padrão: abertas, pelo vencimento
create index ix_tasks_open_due on tasks (due_at) where completed_at is null;
create index ix_tasks_owner on tasks (owner_id, due_at);
create index ix_tasks_deal on tasks (deal_id) where deal_id is not null;
create index ix_tasks_tender on tasks (tender_id) where tender_id is not null;
