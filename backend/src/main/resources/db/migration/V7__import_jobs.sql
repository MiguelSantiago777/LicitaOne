-- Importação de planilhas (Fase 2): metadados do job; o arquivo e o relatório ficam no FileStorage
create table import_jobs (
    id               uuid primary key,
    version          bigint        not null default 0,
    kind             varchar(30)   not null,
    contract_id      uuid          not null references contracts (id),
    file_name        varchar(255)  not null,
    file_key         varchar(255)  not null,
    status           varchar(20)   not null,
    sheet_name       varchar(100),
    header_row       integer,
    mapping          text,
    order_date       date,
    total_rows       integer,
    imported_rows    integer,
    error_rows       integer,
    warning_rows     integer,
    order_value      numeric(15, 2),
    report_key       varchar(255),
    error_message    varchar(1000),
    created_by_id    uuid          not null,
    created_by_name  varchar(120),
    created_by_roles varchar(100),
    created_at       timestamptz   not null,
    queued_at        timestamptz,
    finished_at      timestamptz
);

create index ix_import_jobs_contract on import_jobs (contract_id, created_at desc);
