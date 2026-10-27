-- DADOS DE DESENVOLVIMENTO (Fase 1): exemplos da especificação (seção 3) e da planilha do cliente.
-- UASG, números de processo, datas e responsáveis são fictícios. Só roda nos perfis de dev/docker
-- (spring.flyway.locations); o perfil prod não inclui db/seed. Idempotente: pode rodar de novo.

-- Responsáveis de exemplo. A senha é um hash inválido: ninguém entra com eles até o admin definir uma senha.
insert into users (id, name, email, password_hash, role, active, version, created_at, created_by, updated_at, updated_by)
values
  ('00000000-0000-4000-8000-000000000001', 'Edwin Lima',   'edwin.lima@shema.local',   '{bcrypt}$2a$10$seed.user.cannot.login.until.reset.by.admin.xxxxxxxxxxxxx', 'SALES', true, 0, now(), 'seed', now(), 'seed'),
  ('00000000-0000-4000-8000-000000000002', 'Carla Mendes', 'carla.mendes@shema.local', '{bcrypt}$2a$10$seed.user.cannot.login.until.reset.by.admin.xxxxxxxxxxxxx', 'SALES', true, 0, now(), 'seed', now(), 'seed'),
  ('00000000-0000-4000-8000-000000000003', 'Rafael Souza', 'rafael.souza@shema.local', '{bcrypt}$2a$10$seed.user.cannot.login.until.reset.by.admin.xxxxxxxxxxxxx', 'SALES', true, 0, now(), 'seed', now(), 'seed')
on conflict do nothing;

insert into agencies (id, name, sphere, city, state, version, created_at, created_by, updated_at, updated_by)
values
  ('10000000-0000-4000-8000-000000000001', 'Prefeitura Municipal de Magé',                  'MUNICIPAL', 'Magé',                 'RJ', 0, now(), 'seed', now(), 'seed'),
  ('10000000-0000-4000-8000-000000000002', 'Brigada de Infantaria Paraquedista',            'FEDERAL',   'Rio de Janeiro',       'RJ', 0, now(), 'seed', now(), 'seed'),
  ('10000000-0000-4000-8000-000000000003', 'MEC · INES Inst. Nac. de Educação de Surdos',   'FEDERAL',   'Rio de Janeiro',       'RJ', 0, now(), 'seed', now(), 'seed'),
  ('10000000-0000-4000-8000-000000000004', 'Comando da 1ª Região Militar',                  'FEDERAL',   'Rio de Janeiro',       'RJ', 0, now(), 'seed', now(), 'seed'),
  ('10000000-0000-4000-8000-000000000005', 'Prefeitura Municipal de Cantagalo',             'MUNICIPAL', 'Cantagalo',            'RJ', 0, now(), 'seed', now(), 'seed'),
  ('10000000-0000-4000-8000-000000000006', 'Prefeitura Municipal de Mesquita',              'MUNICIPAL', 'Mesquita',             'RJ', 0, now(), 'seed', now(), 'seed'),
  ('10000000-0000-4000-8000-000000000007', 'Prefeitura de Conceição de Macabu',             'MUNICIPAL', 'Conceição de Macabu',  'RJ', 0, now(), 'seed', now(), 'seed'),
  ('10000000-0000-4000-8000-000000000008', 'SEGOV · Secretaria de Governo',                 'ESTADUAL',  'Rio de Janeiro',       'RJ', 0, now(), 'seed', now(), 'seed'),
  ('10000000-0000-4000-8000-000000000009', 'Prefeitura de São João da Barra',               'MUNICIPAL', 'São João da Barra',    'RJ', 0, now(), 'seed', now(), 'seed'),
  ('10000000-0000-4000-8000-000000000010', 'Colégio Pedro II',                              'FEDERAL',   'Rio de Janeiro',       'RJ', 0, now(), 'seed', now(), 'seed'),
  ('10000000-0000-4000-8000-000000000011', 'SEFAZ · Secretaria de Fazenda',                 'ESTADUAL',  'Rio de Janeiro',       'RJ', 0, now(), 'seed', now(), 'seed'),
  ('10000000-0000-4000-8000-000000000012', 'Comando do Exército',                           'FEDERAL',   'Brasília',             'DF', 0, now(), 'seed', now(), 'seed'),
  ('10000000-0000-4000-8000-000000000013', 'Intendência da Marinha',                        'FEDERAL',   'Rio de Janeiro',       'RJ', 0, now(), 'seed', now(), 'seed'),
  ('10000000-0000-4000-8000-000000000014', 'SEIJES · Secretaria de Igualdade',              'ESTADUAL',  'Rio de Janeiro',       'RJ', 0, now(), 'seed', now(), 'seed'),
  ('10000000-0000-4000-8000-000000000015', 'Casa Civil',                                    'ESTADUAL',  'Rio de Janeiro',       'RJ', 0, now(), 'seed', now(), 'seed'),
  ('10000000-0000-4000-8000-000000000016', 'AGEVAP · Agência de Bacias',                    'OUTRA',     'Resende',              'RJ', 0, now(), 'seed', now(), 'seed'),
  ('10000000-0000-4000-8000-000000000017', 'Prefeitura Municipal de Nova Iguaçu',           'MUNICIPAL', 'Nova Iguaçu',          'RJ', 0, now(), 'seed', now(), 'seed'),
  ('10000000-0000-4000-8000-000000000018', 'Prefeitura Municipal de Petrópolis',            'MUNICIPAL', 'Petrópolis',           'RJ', 0, now(), 'seed', now(), 'seed')
on conflict do nothing;

insert into tenders (id, agency_id, modality, uasg, process_number, portal, session_at, object, material_type, items_quantity, total_value,
                     status, won_at, signed_at, orders_started_at, closed_at, owner_id, version, created_at, created_by, updated_at, updated_by)
values
  -- em disputa (sessões marcadas)
  ('20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000017', 'PREGAO_ELETRONICO', null, '061/2026', 'Compras.gov.br', '2026-11-05 10:00-03', 'Registro de preços de material gráfico para campanhas educativas', 'Material gráfico', 3200, 186500.00, 'EM_DISPUTA', null, null, null, null, '00000000-0000-4000-8000-000000000001', 0, now(), 'seed', now(), 'seed'),
  ('20000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000018', 'PREGAO_ELETRONICO', null, '044/2026', 'BLL Compras', '2026-11-12 09:30-03', 'Kit de material escolar para a rede municipal 2027', 'Kit material escolar', 9000, 412000.00, 'EM_DISPUTA', null, null, null, null, '00000000-0000-4000-8000-000000000003', 0, now(), 'seed', now(), 'seed'),
  -- vencidas aguardando contrato
  ('20000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', 'PREGAO_ELETRONICO', null, '041/2026', 'BLL Compras', null, 'Material de eletro, papelaria, artigos e utensílios', 'Eletro e papelaria', 80, 174000.00, 'VENCIDA', '2026-09-18', null, null, null, '00000000-0000-4000-8000-000000000001', 0, now(), 'seed', now(), 'seed'),
  ('20000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000002', 'PREGAO_ELETRONICO', '160221', '012/2026', 'Compras.gov.br', null, 'Material de expediente escolar', 'Expediente escolar', 3000, 425034.07, 'AGUARDANDO_ASSINATURA', '2026-09-25', null, null, null, '00000000-0000-4000-8000-000000000002', 0, now(), 'seed', now(), 'seed'),
  ('20000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000003', 'PREGAO_ELETRONICO', '152005', '027/2026', 'Compras.gov.br', null, 'Material gráfico institucional', 'Material gráfico', 1200, 425232.00, 'VENCIDA', '2026-10-02', null, null, null, '00000000-0000-4000-8000-000000000001', 0, now(), 'seed', now(), 'seed'),
  ('20000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000004', 'PREGAO_ELETRONICO', '160313', '039/2026', 'Compras.gov.br', null, 'Material gráfico (banners e adesivos perfurados)', 'Material gráfico', 460, 70900.26, 'VENCIDA', '2026-10-09', null, null, null, '00000000-0000-4000-8000-000000000003', 0, now(), 'seed', now(), 'seed'),
  ('20000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000005', 'PREGAO_ELETRONICO', null, '018/2026', 'Licitanet', null, 'Material de expediente escolar', 'Expediente escolar', 6950, 287201.54, 'VENCIDA', '2026-10-14', null, null, null, '00000000-0000-4000-8000-000000000002', 0, now(), 'seed', now(), 'seed'),
  ('20000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000006', 'PREGAO_ELETRONICO', null, '055/2026', 'BLL Compras', null, 'Lixeiras para a rede pública', 'Lixeiras', 1000, 310000.00, 'VENCIDA', '2026-10-20', null, null, null, '00000000-0000-4000-8000-000000000001', 0, now(), 'seed', now(), 'seed'),
  ('20000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000007', 'PREGAO_ELETRONICO', null, '022/2026', 'Portal de Compras Públicas', null, 'Kit de material escolar', 'Kit material escolar', 5000, 213000.00, 'VENCIDA', '2026-10-22', null, null, null, '00000000-0000-4000-8000-000000000003', 0, now(), 'seed', now(), 'seed'),
  -- contratos assinados (sem pedido)
  ('20000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000008', 'PREGAO_ELETRONICO', '926105', '031/2026', 'Compras.gov.br', null, 'Material gráfico diverso', 'Material gráfico', 31000, 89000.00, 'CONTRATO_ASSINADO', '2026-04-10', '2026-05-16', null, null, '00000000-0000-4000-8000-000000000001', 0, now(), 'seed', now(), 'seed'),
  ('20000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000009', 'PREGAO_ELETRONICO', null, '009/2026', 'Licitanet', null, 'Material gráfico', 'Material gráfico', 60, 72570.30, 'CONTRATO_ASSINADO', '2026-08-12', '2026-09-02', null, null, '00000000-0000-4000-8000-000000000002', 0, now(), 'seed', now(), 'seed'),
  ('20000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000010', 'PREGAO_ELETRONICO', '153167', '044/2026', 'Compras.gov.br', null, 'Material de expediente escolar', 'Expediente escolar', 14000, 3685423.59, 'CONTRATO_ASSINADO', '2026-09-05', '2026-10-10', null, null, '00000000-0000-4000-8000-000000000003', 0, now(), 'seed', now(), 'seed'),
  -- em andamento
  ('20000000-0000-4000-8000-000000000013', '10000000-0000-4000-8000-000000000011', 'PREGAO_ELETRONICO', '926201', '017/2026', 'Compras.gov.br', null, 'Material gráfico diverso (cartões, blocos, adesivos, banners)', 'Material gráfico', 8125, 237000.00, 'EM_ANDAMENTO', '2026-02-03', '2026-03-01', '2026-03-20', null, '00000000-0000-4000-8000-000000000001', 0, now(), 'seed', now(), 'seed'),
  ('20000000-0000-4000-8000-000000000014', '10000000-0000-4000-8000-000000000008', 'PREGAO_ELETRONICO', '926105', '033/2026', 'Compras.gov.br', null, 'Uniformes personalizados (coletes, bermudas, calças)', 'Uniformes', 2000, 162000.00, 'EM_ANDAMENTO', '2026-03-15', '2026-04-02', '2026-04-20', null, '00000000-0000-4000-8000-000000000002', 0, now(), 'seed', now(), 'seed'),
  ('20000000-0000-4000-8000-000000000015', '10000000-0000-4000-8000-000000000012', 'PREGAO_ELETRONICO', '160502', '008/2026', 'Compras.gov.br', null, 'Material de expediente', 'Expediente', 8000, 514721.20, 'EM_ANDAMENTO', '2026-01-20', '2026-02-11', '2026-03-02', null, '00000000-0000-4000-8000-000000000003', 0, now(), 'seed', now(), 'seed'),
  ('20000000-0000-4000-8000-000000000016', '10000000-0000-4000-8000-000000000013', 'PREGAO_ELETRONICO', '765701', '003/2026', 'Compras.gov.br', null, 'Eletrodomésticos e material de expediente', 'Eletrodomésticos e expediente', 6000, 4593738.69, 'EM_ANDAMENTO', '2026-01-08', '2026-01-30', '2026-02-15', null, '00000000-0000-4000-8000-000000000001', 0, now(), 'seed', now(), 'seed'),
  -- encerrados
  ('20000000-0000-4000-8000-000000000017', '10000000-0000-4000-8000-000000000014', 'PREGAO_ELETRONICO', '926330', '014/2025', 'Compras.gov.br', null, 'Material gráfico para campanhas de conscientização', 'Material gráfico', 28848, 19990.00, 'ENCERRADO', '2025-11-04', '2025-12-01', '2025-12-15', '2026-09-30', '00000000-0000-4000-8000-000000000002', 0, now(), 'seed', now(), 'seed'),
  ('20000000-0000-4000-8000-000000000018', '10000000-0000-4000-8000-000000000015', 'DISPENSA', null, '112/2026', null, null, 'Placas de identificação e letras em aço', 'Placas e letras em aço', 49, 3800.00, 'ENCERRADO', '2026-06-02', '2026-06-10', '2026-06-12', '2026-08-15', '00000000-0000-4000-8000-000000000001', 0, now(), 'seed', now(), 'seed'),
  ('20000000-0000-4000-8000-000000000019', '10000000-0000-4000-8000-000000000016', 'DISPENSA', null, '034/2026', null, null, 'Banners, revistas, folders e cartões de visita', 'Material gráfico', 24210, 25312.00, 'ENCERRADO', '2026-05-05', '2026-05-12', '2026-05-15', '2026-07-20', '00000000-0000-4000-8000-000000000003', 0, now(), 'seed', now(), 'seed')
on conflict do nothing;

insert into tender_events (id, tender_id, type, to_status, event_date, user_name, occurred_at)
select gen_random_uuid(), t.id, 'CREATED', t.status, t.won_at, 'Seed de desenvolvimento', now()
from tenders t
where t.created_by = 'seed'
  and not exists (select 1 from tender_events e where e.tender_id = t.id);
