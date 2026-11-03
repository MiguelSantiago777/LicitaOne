-- Leitura de atas e contratos com IA (Fase 7, ADR 0016). A IA só sugere: o resultado fica aqui até a pessoa conferir
-- e salvar pelo formulário de sempre.
create table document_extractions (
    id                uuid primary key,
    version           bigint        not null default 0,
    document_id       uuid          not null references documents (id),
    document_version  integer       not null,
    tender_id         uuid          not null references tenders (id),
    kind              varchar(10)   not null check (kind in ('ATA', 'CONTRATO')),
    status            varchar(10)   not null check (status in ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
    result            text,
    model             varchar(60),
    input_tokens      bigint,
    output_tokens     bigint,
    error_message     varchar(1000),
    created_by_id     uuid          not null,
    created_by_name   varchar(120),
    created_at        timestamptz   not null,
    started_at        timestamptz,
    finished_at       timestamptz
);

create index ix_document_extractions_document on document_extractions (document_id, created_at desc);
