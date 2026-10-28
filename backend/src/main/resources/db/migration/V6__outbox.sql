-- Outbox (ADR 0003, ADR 0012): Event Publication Registry do Spring Modulith 2.1, esquema v2 para PostgreSQL,
-- copiado de spring-modulith-events-jdbc (schemas/v2/schema-postgresql.sql). A criação automática fica desligada.
create table if not exists event_publication
(
  id                     uuid                     not null,
  listener_id            text                     not null,
  event_type             text                     not null,
  serialized_event       text                     not null,
  publication_date       timestamp with time zone not null,
  completion_date        timestamp with time zone,
  status                 text,
  completion_attempts    int,
  last_resubmission_date timestamp with time zone,
  primary key (id)
);
create index if not exists event_publication_serialized_event_hash_idx on event_publication using hash(serialized_event);
create index if not exists event_publication_by_completion_date_idx on event_publication (completion_date);

-- Consumidores idempotentes: a mesma mensagem (eventId) só tem efeito uma vez por consumidor
create table processed_messages (
    message_id   uuid        not null,
    consumer     varchar(80) not null,
    processed_at timestamptz not null,
    primary key (message_id, consumer)
);
