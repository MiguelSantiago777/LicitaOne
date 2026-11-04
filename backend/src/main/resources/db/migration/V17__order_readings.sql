-- Pedido a partir de PDF, foto ou mensagem (Fase 8). Como na leitura da ata, a IA só sugere: o resultado fica aqui
-- até a pessoa conferir e registrar o pedido pelo formulário de sempre.
create table order_readings (
    id                uuid primary key,
    version           bigint        not null default 0,
    contract_id       uuid          not null references contracts (id),
    tender_id         uuid          not null references tenders (id),
    source            varchar(10)   not null check (source in ('DOCUMENT', 'TEXT')),
    document_id       uuid          references documents (id),
    document_version  integer,
    -- Texto colado (e-mail, WhatsApp): fica só até a leitura terminar e é apagado depois. Pode ter nome e telefone
    -- de servidor; nunca vai para os logs.
    source_text       text,
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
    finished_at       timestamptz,
    constraint ck_order_readings_source check (
        (source = 'DOCUMENT' and document_id is not null and document_version is not null)
        or (source = 'TEXT' and document_id is null))
);

create index ix_order_readings_contract on order_readings (contract_id, created_at desc);

-- Documento de onde o pedido foi lido (ofício, AF, empenho), para o link no pedido.
alter table contract_orders add column source_document_id uuid references documents (id);
