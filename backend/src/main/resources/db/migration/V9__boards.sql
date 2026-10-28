-- Quadros kanban livres (adiantados da Fase 4; ver docs/decisoes.md, "Quadros kanban")
create table boards (
    id         uuid primary key,
    name       varchar(80) not null,
    position   integer     not null,
    version    bigint      not null default 0,
    created_at timestamptz not null,
    created_by varchar(64),
    updated_at timestamptz not null,
    updated_by varchar(64)
);

create table board_columns (
    id       uuid primary key,
    board_id uuid        not null references boards (id) on delete cascade,
    name     varchar(60) not null,
    position integer     not null
);

create index ix_board_columns_board on board_columns (board_id, position);

create table board_cards (
    id         uuid primary key,
    board_id   uuid          not null references boards (id) on delete cascade,
    column_id  uuid          not null references board_columns (id),
    position   integer       not null,
    title      varchar(200)  not null,
    notes      varchar(2000),
    owner_id   uuid references users (id),
    due_date   date,
    tender_id  uuid references tenders (id),
    version    bigint        not null default 0,
    created_at timestamptz   not null,
    created_by varchar(64),
    updated_at timestamptz   not null,
    updated_by varchar(64)
);

create index ix_board_cards_column on board_cards (column_id, position);
create index ix_board_cards_board on board_cards (board_id);

-- Os dois quadros pedidos pelo cliente. Começam sem colunas: a equipe monta do jeito dela.
insert into boards (id, name, position, version, created_at, created_by, updated_at, updated_by)
values
  ('40000000-0000-4000-8000-000000000001', 'Organização mensal',   0, 0, now(), 'sistema', now(), 'sistema'),
  ('40000000-0000-4000-8000-000000000002', 'Licitações vencidas',  1, 0, now(), 'sistema', now(), 'sistema');
