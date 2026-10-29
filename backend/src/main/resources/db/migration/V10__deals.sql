-- Funil de vendas (Negociações), seção 5.5 da especificação; ADR 0013
create table deal_pipelines (
    id         uuid primary key,
    name       varchar(80) not null,
    version    bigint      not null default 0,
    created_at timestamptz not null,
    created_by varchar(64),
    updated_at timestamptz not null,
    updated_by varchar(64)
);

create table deal_stages (
    id          uuid primary key,
    pipeline_id uuid        not null references deal_pipelines (id) on delete cascade,
    name        varchar(60) not null,
    position    integer     not null,
    kind        varchar(10) not null check (kind in ('ABERTA', 'GANHA', 'PERDIDA'))
);

create index ix_deal_stages_pipeline on deal_stages (pipeline_id, position);

create table deals (
    id          uuid primary key,
    pipeline_id uuid           not null references deal_pipelines (id),
    stage_id    uuid           not null references deal_stages (id),
    position    integer        not null,
    status      varchar(12)    not null check (status in ('EM_ANDAMENTO', 'GANHA', 'PERDIDA')),
    closed_at   date,
    title       varchar(200)   not null,
    agency_id   uuid references agencies (id),
    tender_id   uuid references tenders (id),
    value       numeric(15, 2) not null check (value >= 0),
    priority    integer        not null check (priority between 1 and 3),
    owner_id    uuid references users (id),
    notes       varchar(2000),
    version     bigint         not null default 0,
    created_at  timestamptz    not null,
    created_by  varchar(64),
    updated_at  timestamptz    not null,
    updated_by  varchar(64),
    constraint ck_deals_agency_or_tender check (agency_id is not null or tender_id is not null)
);

create index ix_deals_stage on deals (stage_id, position);
create index ix_deals_pipeline on deals (pipeline_id);
create index ix_deals_tender on deals (tender_id);

-- O funil e as etapas da seção 5.5. O administrador pode renomear, reordenar, criar e excluir.
insert into deal_pipelines (id, name, version, created_at, created_by, updated_at, updated_by)
values ('50000000-0000-4000-8000-000000000001', 'Funil de vendas', 0, now(), 'sistema', now(), 'sistema');

insert into deal_stages (id, pipeline_id, name, position, kind)
values
  ('51000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001', 'Sem contato',                0, 'ABERTA'),
  ('51000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-000000000001', 'Contato feito',              1, 'ABERTA'),
  ('51000000-0000-4000-8000-000000000003', '50000000-0000-4000-8000-000000000001', 'Identificação do interesse', 2, 'ABERTA'),
  ('51000000-0000-4000-8000-000000000004', '50000000-0000-4000-8000-000000000001', 'Apresentação',               3, 'ABERTA'),
  ('51000000-0000-4000-8000-000000000005', '50000000-0000-4000-8000-000000000001', 'Proposta enviada',           4, 'ABERTA'),
  ('51000000-0000-4000-8000-000000000006', '50000000-0000-4000-8000-000000000001', 'Fechado ganho',              5, 'GANHA'),
  ('51000000-0000-4000-8000-000000000007', '50000000-0000-4000-8000-000000000001', 'Fechado perdido',            6, 'PERDIDA');
