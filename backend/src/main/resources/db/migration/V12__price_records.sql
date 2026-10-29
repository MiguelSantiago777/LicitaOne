-- Atas, caronas e documentos (seção 5.3 da especificação); ADR 0014

-- Documentos da licitação. Apagar é lógico: documento jurídico não some (seção 7).
create table documents (
    id              uuid primary key,
    tender_id       uuid         not null references tenders (id),
    kind            varchar(10)  not null check (kind in ('ATA', 'CONTRATO', 'EMPENHO', 'ATESTADO', 'EDITAL', 'OUTROS')),
    title           varchar(200) not null,
    document_date   date,
    current_version integer      not null,
    deleted_at      timestamptz,
    deleted_by      varchar(120),
    version         bigint       not null default 0,
    created_at      timestamptz  not null,
    created_by      varchar(64),
    updated_at      timestamptz  not null,
    updated_by      varchar(64)
);

create index ix_documents_tender on documents (tender_id) where deleted_at is null;

-- Cada envio de arquivo é uma versão; a anterior nunca é sobrescrita.
create table document_versions (
    id               uuid primary key,
    document_id      uuid         not null references documents (id),
    number           integer      not null,
    file_name        varchar(255) not null,
    content_type     varchar(120) not null,
    size_bytes       bigint       not null check (size_bytes > 0),
    sha256           varchar(64)  not null,
    storage_key      varchar(300) not null unique,
    uploaded_by      uuid references users (id),
    uploaded_by_name varchar(120),
    uploaded_at      timestamptz  not null,
    unique (document_id, number)
);

-- Ata de registro de preços: uma por licitação (ADR 0005), com vigência própria.
create table price_records (
    id              uuid primary key,
    tender_id       uuid        not null unique references tenders (id),
    number          varchar(60) not null,
    signed_at       date,
    valid_until     date        not null,
    allows_adhesion boolean     not null,
    notes           varchar(2000),
    document_id     uuid references documents (id),
    version         bigint      not null default 0,
    created_at      timestamptz not null,
    created_by      varchar(64),
    updated_at      timestamptz not null,
    updated_by      varchar(64),
    constraint ck_price_records_dates check (signed_at is null or signed_at <= valid_until)
);

create index ix_price_records_valid_until on price_records (valid_until);

-- Quantitativo registrado na ata. Base dos limites de carona por item.
create table price_record_items (
    id              uuid primary key,
    price_record_id uuid           not null references price_records (id) on delete cascade,
    position        integer        not null,
    lot             varchar(40),
    description     varchar(500)   not null,
    unit            varchar(20),
    quantity        numeric(15, 3) not null check (quantity > 0),
    unit_price      numeric(15, 4) not null check (unit_price >= 0)
);

create index ix_price_record_items_record on price_record_items (price_record_id, position);

-- Carona: outro órgão aderiu à ata (resposta 4 do cliente).
create table caronas (
    id                uuid primary key,
    price_record_id   uuid           not null references price_records (id) on delete cascade,
    agency_id         uuid           not null references agencies (id),
    adhered_at        date           not null,
    value             numeric(15, 2) not null check (value >= 0),
    deal_id           uuid references deals (id) on delete set null,
    notes             varchar(2000),
    -- Limites configuráveis passados com confirmação explícita (ADR 0014): quem confirmou e o quê
    limits_overridden boolean        not null default false,
    override_note     varchar(1000),
    created_by_name   varchar(120),
    created_at        timestamptz    not null
);

create index ix_caronas_record on caronas (price_record_id);
create index ix_caronas_agency on caronas (agency_id);

create table carona_lines (
    carona_id uuid           not null references caronas (id) on delete cascade,
    item_id   uuid           not null references price_record_items (id),
    quantity  numeric(15, 3) not null check (quantity > 0),
    primary key (carona_id, item_id)
);

-- Histórico de ata, itens, caronas e documentos, mostrado junto com o da licitação.
create table price_record_events (
    id          uuid primary key,
    tender_id   uuid         not null references tenders (id),
    type        varchar(30)  not null,
    summary     varchar(500) not null,
    user_id     uuid,
    user_name   varchar(120),
    occurred_at timestamptz  not null
);

create index ix_price_record_events_tender on price_record_events (tender_id, occurred_at);
