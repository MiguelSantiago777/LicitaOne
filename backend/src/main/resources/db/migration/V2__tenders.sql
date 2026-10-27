-- Órgãos, licitações e histórico (Fase 1)
create extension if not exists pg_trgm;

create table agencies (
    id           uuid primary key,
    name         varchar(200) not null,
    sphere       varchar(20)  not null check (sphere in ('MUNICIPAL', 'ESTADUAL', 'FEDERAL', 'OUTRA')),
    city         varchar(120) not null,
    state        varchar(2)   not null,
    cnpj         varchar(14),
    default_uasg varchar(6),
    notes        varchar(2000),
    version      bigint       not null default 0,
    created_at   timestamptz  not null,
    created_by   varchar(64),
    updated_at   timestamptz  not null,
    updated_by   varchar(64)
);

create unique index ux_agencies_name_city on agencies (lower(name), lower(city));
create index ix_agencies_name_trgm on agencies using gin (lower(name) gin_trgm_ops);

create table tenders (
    id                uuid primary key,
    agency_id         uuid          not null references agencies (id),
    modality          varchar(30)   not null,
    uasg              varchar(6),
    process_number    varchar(10),
    portal            varchar(80),
    session_at        timestamptz,
    object            varchar(2000) not null,
    material_type     varchar(120)  not null,
    items_quantity    integer check (items_quantity >= 0),
    total_value       numeric(15, 2) not null check (total_value >= 0),
    status            varchar(30)   not null,
    won_at            date,
    signed_at         date,
    orders_started_at date,
    closed_at         date,
    owner_id          uuid references users (id),
    notes             varchar(4000),
    version           bigint        not null default 0,
    created_at        timestamptz   not null,
    created_by        varchar(64),
    updated_at        timestamptz   not null,
    updated_by        varchar(64),
    constraint ck_tenders_identification check (uasg is not null or process_number is not null)
);

create index ix_tenders_status on tenders (status);
create index ix_tenders_agency on tenders (agency_id);
create index ix_tenders_owner on tenders (owner_id);
create index ix_tenders_signed on tenders (signed_at) where status = 'CONTRATO_ASSINADO';
create index ix_tenders_object_trgm on tenders using gin (lower(object) gin_trgm_ops);

create table tender_events (
    id          uuid primary key,
    tender_id   uuid        not null references tenders (id),
    type        varchar(20) not null,
    from_status varchar(30),
    to_status   varchar(30),
    event_date  date,
    changes     text,
    user_id     uuid,
    user_name   varchar(120),
    occurred_at timestamptz not null
);

create index ix_tender_events_tender on tender_events (tender_id, occurred_at desc);
