-- Registro de contato com o órgão (Fase 9): o que foi conversado, por qual canal e quando. Aparece no histórico da
-- licitação e é o contexto do follow-up escrito com IA.
create table tender_contacts (
    id               uuid primary key,
    tender_id        uuid          not null references tenders (id),
    contract_id      uuid          references contracts (id),
    channel          varchar(20)   not null check (channel in ('EMAIL', 'WHATSAPP', 'TELEFONE', 'REUNIAO', 'OUTRO')),
    outcome          varchar(30)   check (outcome in ('PEDIDO_PREVISTO', 'SEM_PREVISAO', 'PEDIDO_A_CAMINHO',
                                                      'NAO_VAI_PEDIR', 'PEDE_DOCUMENTO', 'OUTRO')),
    contacted_at     date          not null,
    summary          varchar(1000) not null,
    suggested_by_ai  boolean       not null default false,
    user_id          uuid          not null,
    user_name        varchar(120),
    created_at       timestamptz   not null
);

create index ix_tender_contacts_tender on tender_contacts (tender_id, contacted_at desc, created_at desc);
