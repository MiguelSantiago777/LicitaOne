-- DADOS DE DESENVOLVIMENTO (Fase 3): atas das licitações do seed e caronas. Cobre ata vigente com itens e carona,
-- ata vencendo, ata vencida, ata que não permite adesão e a de contrato encerrado ainda válida até 2028 (o exemplo da
-- especificação). Sem documentos: o arquivo teria que existir no S3. Idempotente: pode rodar de novo.

insert into price_records (id, tender_id, number, signed_at, valid_until, allows_adhesion, notes, document_id,
                           version, created_at, created_by, updated_at, updated_by)
values
  -- SEFAZ (em andamento): itens registrados e uma carona
  ('54000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000013', 'ARP 017/2026', '2026-03-01', '2027-02-28', true,
   null, null, 0, now(), 'seed', now(), 'seed'),
  -- SEIJES (contrato encerrado em 09/2026): a ata continua valendo até 2028
  ('54000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000017', 'ARP 014/2025', '2025-11-04', '2028-11-04', true,
   'Contrato encerrado, mas a ata segue vigente: boa para oferecer a outros municípios.', null, 0, now(), 'seed', now(), 'seed'),
  -- Comando do Exército: vence em dezembro, já tem a carona de Cantagalo (negociação ganha)
  ('54000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000015', 'ARP 008/2026', '2026-02-01', '2026-12-10', true,
   null, null, 0, now(), 'seed', now(), 'seed'),
  ('54000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000016', 'ARP 003/2026', '2026-01-30', '2027-01-30', true,
   null, null, 0, now(), 'seed', now(), 'seed'),
  -- Colégio Pedro II: o edital vedou adesão
  ('54000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000012', 'ARP 044/2026', '2026-09-05', '2027-09-05', false,
   'O edital não permite adesão de outros órgãos.', null, 0, now(), 'seed', now(), 'seed'),
  -- SEGOV uniformes: vencida
  ('54000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000014', 'ARP 033/2026', '2025-09-30', '2026-09-30', true,
   null, null, 0, now(), 'seed', now(), 'seed')
on conflict do nothing;

insert into price_record_items (id, price_record_id, position, lot, description, unit, quantity, unit_price)
values
  ('55000000-0000-4000-8000-000000000001', '54000000-0000-4000-8000-000000000001', 0, null, 'Cartão de visita 4x4 cores', 'un', 5000, 0.4000),
  ('55000000-0000-4000-8000-000000000002', '54000000-0000-4000-8000-000000000001', 1, null, 'Bloco de anotações A5 50 folhas', 'un', 2000, 12.5000),
  ('55000000-0000-4000-8000-000000000003', '54000000-0000-4000-8000-000000000001', 2, null, 'Adesivo perfurado 1,00 x 0,50 m', 'un', 1000, 30.0000),
  ('55000000-0000-4000-8000-000000000004', '54000000-0000-4000-8000-000000000001', 3, null, 'Banner lona 440 g 3,00 x 2,00 m', 'un', 125, 1440.0000)
on conflict do nothing;

insert into caronas (id, price_record_id, agency_id, adhered_at, value, deal_id, notes, limits_overridden, override_note,
                     created_by_name, created_at)
values
  ('56000000-0000-4000-8000-000000000001', '54000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000006', '2026-07-15', 98400.00,
   null, 'Secretaria de Educação de Mesquita.', false, null, 'Seed de desenvolvimento', now()),
  ('56000000-0000-4000-8000-000000000002', '54000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000005', '2026-10-15', 128000.00,
   '52000000-0000-4000-8000-000000000007', null, false, null, 'Seed de desenvolvimento', now())
on conflict do nothing;

insert into carona_lines (carona_id, item_id, quantity)
values
  ('56000000-0000-4000-8000-000000000001', '55000000-0000-4000-8000-000000000004', 60),
  ('56000000-0000-4000-8000-000000000001', '55000000-0000-4000-8000-000000000003', 400)
on conflict do nothing;
