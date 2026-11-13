-- Propostas de preços (Fase 12, ADR 0022). A proposta é salva enquanto a pessoa digita, então quase tudo pode ficar
-- em branco; o que falta é conferido na hora de gerar o PDF.

-- Cargo e CPF de quem assina as propostas
alter table users add column job_title varchar(120);
alter table users add column cpf varchar(11);

-- Último número usado em cada ano. A criação da proposta incrementa a linha do ano na mesma transação: duas pessoas
-- criando ao mesmo tempo nunca recebem o mesmo número, e um número dado não volta.
create table proposal_counters (
    year        integer primary key,
    last_number integer not null
);

create table proposals (
    id              uuid primary key,
    number_year     integer       not null,
    number_seq      integer       not null,
    kind            varchar(10)   not null check (kind in ('PRODUTO', 'SERVICO')),
    client_name     varchar(200),
    client_cnpj     varchar(14),
    client_address  varchar(300),
    client_email    varchar(254),
    client_phones   varchar(120),
    validity_days   integer check (validity_days > 0),
    payment_terms   varchar(500),
    delivery_terms  varchar(500),
    proposal_date   date          not null,
    signer_name     varchar(120),
    signer_role     varchar(120),
    signer_cpf      varchar(11),
    -- Soma dos itens, recalculada a cada alteração: a lista mostra sem ler os itens
    total           numeric(15, 2) not null default 0,
    owner_id        uuid          not null references users (id),
    cancelled_at    timestamptz,
    cancelled_by    uuid references users (id),
    version         bigint        not null default 0,
    created_at      timestamptz   not null,
    created_by      varchar(64),
    updated_at      timestamptz   not null,
    updated_by      varchar(64),
    constraint ux_proposals_number unique (number_year, number_seq)
);

create index ix_proposals_date on proposals (proposal_date desc, number_year desc, number_seq desc);

create table proposal_items (
    id          uuid primary key,
    proposal_id uuid           not null references proposals (id) on delete cascade,
    position    integer        not null,
    item_number varchar(20),
    description varchar(2000),
    unit        varchar(20),
    brand       varchar(100),
    quantity    numeric(15, 3) check (quantity > 0),
    unit_price  numeric(15, 4) check (unit_price >= 0)
);

create index ix_proposal_items_proposal on proposal_items (proposal_id, position);
