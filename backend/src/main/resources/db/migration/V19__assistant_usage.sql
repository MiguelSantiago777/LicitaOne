-- Perguntas ao assistente por pessoa e por dia (Fase 11, ADR 0020): só a contagem, para o limite diário.
-- O texto das perguntas e das respostas não é guardado (decisão de 04/11/2026).
create table assistant_usage (
    user_id   uuid    not null references users (id),
    day       date    not null,
    questions integer not null check (questions >= 0),
    primary key (user_id, day)
);
