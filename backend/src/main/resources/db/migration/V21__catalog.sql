-- Fornecedores e produtos por nicho (Fase 13, ADR 0023). O fornecedor atende um ou mais nichos, o produto fica em um
-- nicho e cada produto tem um ou mais fornecedores, cada um com o seu preço de custo (oferta). Nada é excluído:
-- nicho, fornecedor e produto são arquivados, e o que estava ligado a eles continua.

create table niches (
    id          uuid primary key,
    name        varchar(80)  not null,
    -- Nome sem acento e em minúsculas: "Limpeza" e "limpéza" são o mesmo nicho
    name_key    varchar(80)  not null,
    description varchar(300),
    archived_at timestamptz,
    version     bigint       not null default 0,
    created_at  timestamptz  not null,
    created_by  varchar(64),
    updated_at  timestamptz  not null,
    updated_by  varchar(64),
    constraint ux_niches_name_key unique (name_key)
);

create table suppliers (
    id          uuid primary key,
    legal_name  varchar(200) not null,
    trade_name  varchar(200),
    cnpj        varchar(14),
    city        varchar(100),
    state       varchar(2),
    website     varchar(200),
    notes       varchar(2000),
    -- Razão social, nome fantasia, CNPJ e cidade sem acento e em minúsculas, para a busca
    search_text varchar(600) not null,
    archived_at timestamptz,
    version     bigint       not null default 0,
    created_at  timestamptz  not null,
    created_by  varchar(64),
    updated_at  timestamptz  not null,
    updated_by  varchar(64)
);

create index ix_suppliers_legal_name on suppliers (lower(legal_name));

create table supplier_contacts (
    id          uuid primary key,
    supplier_id uuid         not null references suppliers (id) on delete cascade,
    position    integer      not null,
    name        varchar(120),
    phone       varchar(40),
    email       varchar(254)
);

create index ix_supplier_contacts_supplier on supplier_contacts (supplier_id, position);

create table supplier_niches (
    supplier_id uuid not null references suppliers (id) on delete cascade,
    niche_id    uuid not null references niches (id),
    primary key (supplier_id, niche_id)
);

create index ix_supplier_niches_niche on supplier_niches (niche_id);

create table products (
    id           uuid primary key,
    name         varchar(200) not null,
    description  varchar(2000),
    niche_id     uuid         not null references niches (id),
    unit         varchar(20),
    brand        varchar(100),
    -- Código CATMAT (material) ou CATSER (serviço) do catálogo do governo
    catalog_code varchar(20),
    -- Nome, marca e código sem acento e em minúsculas, para a busca
    search_text  varchar(400) not null,
    archived_at  timestamptz,
    version      bigint       not null default 0,
    created_at   timestamptz  not null,
    created_by   varchar(64),
    updated_at   timestamptz  not null,
    updated_by   varchar(64)
);

create index ix_products_niche on products (niche_id);
create index ix_products_name on products (lower(name));

-- Preço de custo de um fornecedor para um produto. Uma oferta por par: atualizar o preço troca a data da cotação.
create table product_offers (
    id             uuid primary key,
    product_id     uuid           not null references products (id) on delete cascade,
    supplier_id    uuid           not null references suppliers (id),
    position       integer        not null,
    unit_price     numeric(15, 4) check (unit_price >= 0),
    quoted_on      date,
    lead_time_days integer        check (lead_time_days >= 0),
    minimum_order  numeric(15, 3) check (minimum_order > 0),
    constraint ux_product_offers_pair unique (product_id, supplier_id)
);

create index ix_product_offers_supplier on product_offers (supplier_id);

-- Foto e ficha técnica. O arquivo fica no FileStorage (disco ou S3); aqui só os metadados e a chave.
create table product_files (
    id           uuid primary key,
    product_id   uuid         not null references products (id) on delete cascade,
    kind         varchar(10)  not null check (kind in ('FOTO', 'FICHA')),
    file_name    varchar(255) not null,
    content_type varchar(100) not null,
    size_bytes   bigint       not null,
    storage_key  varchar(300) not null,
    uploaded_by  varchar(120),
    uploaded_at  timestamptz  not null
);

create index ix_product_files_product on product_files (product_id, uploaded_at);
