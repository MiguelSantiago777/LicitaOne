-- Distingue token trocado (rotação) de token revogado por logout ou suspeita de roubo (janela de corrida)
alter table refresh_tokens add column rotated boolean not null default false;
