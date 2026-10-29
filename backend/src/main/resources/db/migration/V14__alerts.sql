-- Alertas e follow-up (seção 5.4, ADR 0015)

-- Rascunho de e-mail ao órgão nas tarefas de alerta (o sistema nunca envia)
alter table tasks
    add column email_to      varchar(500),
    add column email_subject varchar(200),
    add column email_body    varchar(4000);

-- ShedLock: o job diário roda uma vez só, mesmo com várias instâncias
create table shedlock (
    name       varchar(64)  primary key,
    lock_until timestamptz  not null,
    locked_at  timestamptz  not null,
    locked_by  varchar(255) not null
);

-- O que já foi avisado: uma linha por chave (regra + assunto + período). O job só publica chave nova.
create table alerts (
    id             uuid         primary key,
    alert_key      varchar(120) not null unique,
    rule           varchar(30)  not null
        check (rule in ('CONTRATO_SEM_PEDIDO', 'ATA_VENCENDO', 'LICITACAO_SEM_CONTRATO')),
    tender_id      uuid         not null references tenders (id),
    -- O contrato, a ata ou a licitação que gerou o alerta
    subject_id     uuid         not null,
    agency_name    varchar(200) not null,
    title          varchar(200) not null,
    due_on         date         not null,
    owner_id       uuid references users (id),
    raised_at      timestamptz  not null,
    -- Preenchidos pelos consumidores
    task_id        uuid,
    task_opened_at timestamptz,
    notified_to    varchar(1000),
    notified_at    timestamptz
);

create index ix_alerts_raised on alerts (raised_at desc);
create index ix_alerts_tender on alerts (tender_id);
