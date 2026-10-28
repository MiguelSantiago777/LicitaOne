-- Contatos dos órgãos (pendência da Fase 1). Dado pessoal: o mínimo para o follow-up (LGPD).
create table agency_contacts (
    id        uuid primary key,
    agency_id uuid         not null references agencies (id) on delete cascade,
    position  integer      not null,
    name      varchar(120) not null,
    role      varchar(80),
    email     varchar(160),
    phone     varchar(13),
    notes     varchar(500)
);

create index ix_agency_contacts_agency on agency_contacts (agency_id, position);
