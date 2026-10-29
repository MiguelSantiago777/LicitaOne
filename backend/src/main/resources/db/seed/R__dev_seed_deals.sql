-- DADOS DE DESENVOLVIMENTO (Fase 4): negociações do funil e tarefas. As negociações prospectam caronas de atas das
-- licitações do seed em outros órgãos. Datas relativas a now(), para sempre haver tarefa atrasada e negociação nova.
-- Idempotente: pode rodar de novo.

insert into deals (id, pipeline_id, stage_id, position, status, closed_at, title, agency_id, tender_id, value, priority,
                   owner_id, notes, version, created_at, created_by, updated_at, updated_by)
values
  -- Sem contato
  ('52000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000001', 0, 'EM_ANDAMENTO', null,
   'Carona da ata SEFAZ · material gráfico', '10000000-0000-4000-8000-000000000017', '20000000-0000-4000-8000-000000000013', 118500.00, 2,
   '00000000-0000-4000-8000-000000000001', 'Secretaria de Educação comentou que vai precisar de banners em 2027.', 0, now() - interval '2 days', 'seed', now(), 'seed'),
  ('52000000-0000-4000-8000-000000000009', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000001', 1, 'EM_ANDAMENTO', null,
   'Material gráfico institucional', '10000000-0000-4000-8000-000000000012', '20000000-0000-4000-8000-000000000013', 95000.00, 1,
   null, null, 0, now() - interval '1 day', 'seed', now(), 'seed'),
  -- Contato feito
  ('52000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000002', 0, 'EM_ANDAMENTO', null,
   'Carona da ata da Intendência · expediente', '10000000-0000-4000-8000-000000000018', '20000000-0000-4000-8000-000000000016', 450000.00, 3,
   '00000000-0000-4000-8000-000000000003', null, 0, now() - interval '20 days', 'seed', now(), 'seed'),
  ('52000000-0000-4000-8000-000000000010', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000002', 1, 'EM_ANDAMENTO', null,
   'Banners e adesivos perfurados', '10000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000006', 70900.26, 2,
   '00000000-0000-4000-8000-000000000001', null, 0, now() - interval '15 days', 'seed', now(), 'seed'),
  -- Identificação do interesse
  ('52000000-0000-4000-8000-000000000003', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000003', 0, 'EM_ANDAMENTO', null,
   'Material gráfico para campanhas', '10000000-0000-4000-8000-000000000007', '20000000-0000-4000-8000-000000000017', 19990.00, 1,
   '00000000-0000-4000-8000-000000000002', null, 0, now() - interval '25 days', 'seed', now(), 'seed'),
  -- Apresentação
  ('52000000-0000-4000-8000-000000000004', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000004', 0, 'EM_ANDAMENTO', null,
   'Kit de material escolar 2027', '10000000-0000-4000-8000-000000000009', null, 213000.00, 3,
   '00000000-0000-4000-8000-000000000001', 'Apresentar os kits por faixa etária.', 0, now() - interval '30 days', 'seed', now(), 'seed'),
  -- Proposta enviada
  ('52000000-0000-4000-8000-000000000005', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000005', 0, 'EM_ANDAMENTO', null,
   'Placas de identificação e letras em aço', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000018', 12500.00, 1,
   '00000000-0000-4000-8000-000000000002', null, 0, now() - interval '40 days', 'seed', now(), 'seed'),
  ('52000000-0000-4000-8000-000000000006', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000005', 1, 'EM_ANDAMENTO', null,
   'Uniformes personalizados', '10000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000014', 162000.00, 2,
   '00000000-0000-4000-8000-000000000003', null, 0, now() - interval '35 days', 'seed', now(), 'seed'),
  -- Fechadas
  ('52000000-0000-4000-8000-000000000007', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000006', 0, 'GANHA', '2026-10-15',
   'Adesão à ata de expediente', '10000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000015', 128000.00, 2,
   '00000000-0000-4000-8000-000000000002', null, 0, now() - interval '60 days', 'seed', now(), 'seed'),
  ('52000000-0000-4000-8000-000000000008', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000007', 0, 'PERDIDA', '2026-10-01',
   'Lixeiras para as unidades', '10000000-0000-4000-8000-000000000010', null, 54000.00, 1,
   '00000000-0000-4000-8000-000000000001', 'O órgão optou por licitação própria.', 0, now() - interval '70 days', 'seed', now(), 'seed')
on conflict do nothing;

insert into tasks (id, title, notes, due_at, owner_id, deal_id, tender_id, origin, alert_key, completed_at, completed_by,
                   version, created_at, created_by, updated_at, updated_by)
values
  -- Licitações vencidas que ainda não receberam o contrato (o print 3.1)
  ('53000000-0000-4000-8000-000000000001', 'Cobrar o envio do contrato para assinatura', null, date_trunc('hour', now()) - interval '6 days',
   '00000000-0000-4000-8000-000000000001', null, '20000000-0000-4000-8000-000000000003', 'MANUAL', null, null, null, 0, now(), 'seed', now(), 'seed'),
  ('53000000-0000-4000-8000-000000000002', 'Conferir a minuta do contrato', null, date_trunc('hour', now()) - interval '2 days',
   '00000000-0000-4000-8000-000000000002', null, '20000000-0000-4000-8000-000000000004', 'MANUAL', null, null, null, 0, now(), 'seed', now(), 'seed'),
  ('53000000-0000-4000-8000-000000000003', 'Cobrar o envio do contrato para assinatura', null, date_trunc('hour', now()) + interval '1 day',
   '00000000-0000-4000-8000-000000000001', null, '20000000-0000-4000-8000-000000000005', 'MANUAL', null, null, null, 0, now(), 'seed', now(), 'seed'),
  ('53000000-0000-4000-8000-000000000004', 'Cobrar o envio do contrato para assinatura', null, date_trunc('hour', now()) + interval '3 days',
   '00000000-0000-4000-8000-000000000003', null, '20000000-0000-4000-8000-000000000006', 'MANUAL', null, null, null, 0, now(), 'seed', now(), 'seed'),
  ('53000000-0000-4000-8000-000000000005', 'Cobrar o envio do contrato para assinatura', null, date_trunc('hour', now()) + interval '5 days',
   '00000000-0000-4000-8000-000000000002', null, '20000000-0000-4000-8000-000000000007', 'MANUAL', null, null, null, 0, now(), 'seed', now(), 'seed'),
  -- Contrato assinado sem pedido
  ('53000000-0000-4000-8000-000000000006', 'Perguntar a previsão de pedidos de materiais', 'Contrato assinado em 16/05 e nenhum pedido até agora.', date_trunc('hour', now()) - interval '10 days',
   '00000000-0000-4000-8000-000000000001', null, '20000000-0000-4000-8000-000000000010', 'MANUAL', null, null, null, 0, now(), 'seed', now(), 'seed'),
  -- Negociações
  ('53000000-0000-4000-8000-000000000007', 'Ligar para o setor de compras', null, date_trunc('hour', now()) - interval '3 days',
   '00000000-0000-4000-8000-000000000001', '52000000-0000-4000-8000-000000000001', null, 'MANUAL', null, null, null, 0, now(), 'seed', now(), 'seed'),
  ('53000000-0000-4000-8000-000000000008', 'Enviar a ata e o atestado de capacidade', null, date_trunc('hour', now()) + interval '2 days',
   '00000000-0000-4000-8000-000000000003', '52000000-0000-4000-8000-000000000002', null, 'MANUAL', null, null, null, 0, now(), 'seed', now(), 'seed'),
  ('53000000-0000-4000-8000-000000000009', 'Apresentar o catálogo de kits', null, date_trunc('hour', now()) + interval '1 day',
   '00000000-0000-4000-8000-000000000001', '52000000-0000-4000-8000-000000000004', null, 'MANUAL', null, null, null, 0, now(), 'seed', now(), 'seed'),
  ('53000000-0000-4000-8000-000000000010', 'Enviar o catálogo de uniformes', null, date_trunc('hour', now()) - interval '1 day',
   '00000000-0000-4000-8000-000000000003', '52000000-0000-4000-8000-000000000006', null, 'MANUAL', null, null, null, 0, now(), 'seed', now(), 'seed'),
  ('53000000-0000-4000-8000-000000000011', 'Confirmar o recebimento da proposta', null, date_trunc('hour', now()) + interval '4 days',
   '00000000-0000-4000-8000-000000000002', '52000000-0000-4000-8000-000000000005', null, 'MANUAL', null, null, null, 0, now(), 'seed', now(), 'seed'),
  -- Concluídas
  ('53000000-0000-4000-8000-000000000012', 'Enviar os documentos da adesão', null, date_trunc('hour', now()) - interval '12 days',
   '00000000-0000-4000-8000-000000000002', '52000000-0000-4000-8000-000000000007', null, 'MANUAL', null, now() - interval '11 days', '00000000-0000-4000-8000-000000000002', 0, now(), 'seed', now(), 'seed'),
  ('53000000-0000-4000-8000-000000000013', 'Agendar a entrega do lote de banners', null, date_trunc('hour', now()) - interval '8 days',
   '00000000-0000-4000-8000-000000000001', null, '20000000-0000-4000-8000-000000000013', 'MANUAL', null, now() - interval '8 days', '00000000-0000-4000-8000-000000000001', 0, now(), 'seed', now(), 'seed')
on conflict do nothing;
