-- Contratos, itens, pedidos e histórico (Fase 2, ADR 0012)

create table contracts (
    id          uuid primary key,
    tender_id   uuid           not null references tenders (id),
    kind        varchar(20)    not null check (kind in ('CONTRATO', 'EMPENHO', 'DISPENSA')),
    number      varchar(40),
    signed_at   date           not null,
    valid_until date,
    closed_at   date,
    value       numeric(15, 2) not null check (value >= 0),
    notes       varchar(4000),
    version     bigint         not null default 0,
    created_at  timestamptz    not null,
    created_by  varchar(64),
    updated_at  timestamptz    not null,
    updated_by  varchar(64),
    constraint ck_contracts_validity check (valid_until is null or valid_until >= signed_at),
    constraint ck_contracts_closed check (closed_at is null or closed_at >= signed_at)
);

create index ix_contracts_tender on contracts (tender_id);
-- "assinado há mais de 30 dias sem pedido" e saldo a pedir: só os contratos abertos interessam
create index ix_contracts_open on contracts (signed_at) where closed_at is null;

create table contract_items (
    id          uuid primary key,
    contract_id uuid           not null references contracts (id) on delete cascade,
    position    integer        not null,
    lot         varchar(40),
    description varchar(500)   not null,
    unit        varchar(20),
    quantity    numeric(15, 3) not null check (quantity > 0),
    unit_price  numeric(15, 4) not null check (unit_price >= 0)
);

create index ix_contract_items_contract on contract_items (contract_id, position);

create table contract_orders (
    id              uuid primary key,
    contract_id     uuid           not null references contracts (id) on delete cascade,
    ordered_at      date           not null,
    reference       varchar(60),
    notes           varchar(2000),
    value           numeric(15, 2) not null check (value > 0),
    created_at      timestamptz    not null,
    created_by_name varchar(120)
);

create index ix_contract_orders_contract on contract_orders (contract_id, ordered_at);

create table contract_order_lines (
    id         uuid primary key,
    order_id   uuid           not null references contract_orders (id) on delete cascade,
    item_id    uuid           not null references contract_items (id),
    quantity   numeric(15, 3) not null check (quantity > 0),
    unit_price numeric(15, 4) not null check (unit_price >= 0),
    total      numeric(15, 2) not null check (total >= 0)
);

create index ix_contract_order_lines_order on contract_order_lines (order_id);
create index ix_contract_order_lines_item on contract_order_lines (item_id);

create table contract_events (
    id          uuid primary key,
    contract_id uuid        not null references contracts (id),
    tender_id   uuid        not null references tenders (id),
    type        varchar(20) not null,
    event_date  date,
    summary     varchar(500),
    changes     text,
    user_id     uuid,
    user_name   varchar(120),
    occurred_at timestamptz not null
);

create index ix_contract_events_contract on contract_events (contract_id, occurred_at desc);
create index ix_contract_events_tender on contract_events (tender_id, occurred_at desc);
