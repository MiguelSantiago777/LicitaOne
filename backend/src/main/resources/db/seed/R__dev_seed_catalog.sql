-- Fornecedores e produtos de exemplo (Fase 13). Só em dev e teste: o perfil prod não lê db/seed.
-- Fornecedores e CNPJs são fictícios.

insert into niches (id, name, name_key, description, archived_at, version, created_at, created_by, updated_at, updated_by)
values
  ('61000000-0000-4000-8000-000000000001', 'Limpeza', 'limpeza', 'Material de limpeza e higiene', null, 0, now(), 'seed', now(), 'seed'),
  ('61000000-0000-4000-8000-000000000002', 'Informática', 'informatica', 'Equipamentos e suprimentos de informática', null, 0, now(), 'seed', now(), 'seed'),
  ('61000000-0000-4000-8000-000000000003', 'Material de escritório', 'material de escritorio', null, null, 0, now(), 'seed', now(), 'seed'),
  ('61000000-0000-4000-8000-000000000004', 'Gráfica', 'grafica', 'Impressos, banners e adesivos', null, 0, now(), 'seed', now(), 'seed'),
  ('61000000-0000-4000-8000-000000000005', 'Uniformes', 'uniformes', null, '2026-10-01 12:00:00+00', 1, now(), 'seed', now(), 'seed')
on conflict do nothing;

insert into suppliers (id, legal_name, trade_name, cnpj, city, state, website, notes, search_text, archived_at,
                       version, created_at, created_by, updated_at, updated_by)
values
  ('62000000-0000-4000-8000-000000000001', 'Alfa Distribuidora de Produtos de Limpeza Ltda', 'Alfa Limpeza', '12345678000195',
   'Duque de Caxias', 'RJ', 'https://alfalimpeza.example', 'Entrega própria na Baixada.',
   'alfa distribuidora de produtos de limpeza ltda alfa limpeza 12345678000195 duque de caxias', null, 0, now(), 'seed', now(), 'seed'),
  ('62000000-0000-4000-8000-000000000002', 'Beta Comércio de Informática Ltda', 'Beta Info', '23456789000195',
   'Rio de Janeiro', 'RJ', null, null,
   'beta comercio de informatica ltda beta info 23456789000195 rio de janeiro', null, 0, now(), 'seed', now(), 'seed'),
  ('62000000-0000-4000-8000-000000000003', 'Gama Papelaria e Escritório Eireli', null, '34567890000130',
   'São Paulo', 'SP', null, 'Pedido mínimo alto, mas preço bom.',
   'gama papelaria e escritorio eireli 34567890000130 sao paulo', null, 0, now(), 'seed', now(), 'seed'),
  ('62000000-0000-4000-8000-000000000004', 'Delta Gráfica Rápida Ltda', 'Delta Gráfica', '45678901000175',
   'Niterói', 'RJ', null, null,
   'delta grafica rapida ltda delta grafica 45678901000175 niteroi', null, 0, now(), 'seed', now(), 'seed'),
  ('62000000-0000-4000-8000-000000000005', 'Épsilon Atacado Ltda', 'Épsilon Atacado', '56789012000100',
   'Belo Horizonte', 'MG', null, null,
   'epsilon atacado ltda epsilon atacado 56789012000100 belo horizonte', null, 0, now(), 'seed', now(), 'seed'),
  ('62000000-0000-4000-8000-000000000006', 'Zeta Suprimentos Ltda', null, '67890123000116',
   'Nova Iguaçu', 'RJ', null, null,
   'zeta suprimentos ltda 67890123000116 nova iguacu', null, 0, now(), 'seed', now(), 'seed'),
  ('62000000-0000-4000-8000-000000000007', 'Eta Confecções Ltda', null, '78901234000105',
   'Petrópolis', 'RJ', null, 'Parou de atender licitação.',
   'eta confeccoes ltda 78901234000105 petropolis', '2026-10-01 12:00:00+00', 1, now(), 'seed', now(), 'seed')
on conflict do nothing;

insert into supplier_contacts (id, supplier_id, position, name, phone, email)
values
  ('62100000-0000-4000-8000-000000000001', '62000000-0000-4000-8000-000000000001', 1, 'Carla Mendes', '(21) 98888-1001', 'vendas@alfalimpeza.example'),
  ('62100000-0000-4000-8000-000000000002', '62000000-0000-4000-8000-000000000001', 2, 'Financeiro', '(21) 3333-1002', null),
  ('62100000-0000-4000-8000-000000000003', '62000000-0000-4000-8000-000000000002', 1, 'Rafael Lima', '(21) 97777-2001', 'rafael@betainfo.example'),
  ('62100000-0000-4000-8000-000000000004', '62000000-0000-4000-8000-000000000003', 1, 'Comercial', '(11) 4004-3001', 'comercial@gama.example'),
  ('62100000-0000-4000-8000-000000000005', '62000000-0000-4000-8000-000000000004', 1, 'Paulo', '(21) 96666-4001', null)
on conflict do nothing;

insert into supplier_niches (supplier_id, niche_id)
values
  ('62000000-0000-4000-8000-000000000001', '61000000-0000-4000-8000-000000000001'),
  ('62000000-0000-4000-8000-000000000002', '61000000-0000-4000-8000-000000000002'),
  ('62000000-0000-4000-8000-000000000003', '61000000-0000-4000-8000-000000000003'),
  ('62000000-0000-4000-8000-000000000003', '61000000-0000-4000-8000-000000000002'),
  ('62000000-0000-4000-8000-000000000004', '61000000-0000-4000-8000-000000000004'),
  ('62000000-0000-4000-8000-000000000005', '61000000-0000-4000-8000-000000000001'),
  ('62000000-0000-4000-8000-000000000005', '61000000-0000-4000-8000-000000000003'),
  ('62000000-0000-4000-8000-000000000006', '61000000-0000-4000-8000-000000000001'),
  ('62000000-0000-4000-8000-000000000006', '61000000-0000-4000-8000-000000000003'),
  ('62000000-0000-4000-8000-000000000007', '61000000-0000-4000-8000-000000000005')
on conflict do nothing;

insert into products (id, name, description, niche_id, unit, brand, catalog_code, search_text, archived_at,
                      version, created_at, created_by, updated_at, updated_by)
values
  ('63000000-0000-4000-8000-000000000001', 'Detergente neutro 500 ml', null, '61000000-0000-4000-8000-000000000001', 'un', 'Limpol', '463270', 'detergente neutro 500 ml limpol 463270', null, 0, now(), 'seed', now(), 'seed'),
  ('63000000-0000-4000-8000-000000000002', 'Água sanitária 1 L', null, '61000000-0000-4000-8000-000000000001', 'un', 'Qboa', null, 'agua sanitaria 1 l qboa', null, 0, now(), 'seed', now(), 'seed'),
  ('63000000-0000-4000-8000-000000000003', 'Papel toalha interfolhado 1000 folhas', null, '61000000-0000-4000-8000-000000000001', 'pct', null, null, 'papel toalha interfolhado 1000 folhas', null, 0, now(), 'seed', now(), 'seed'),
  ('63000000-0000-4000-8000-000000000004', 'Toner compatível HP 85A', null, '61000000-0000-4000-8000-000000000002', 'un', null, '400215', 'toner compativel hp 85a 400215', null, 0, now(), 'seed', now(), 'seed'),
  ('63000000-0000-4000-8000-000000000005', 'Mouse óptico USB', null, '61000000-0000-4000-8000-000000000002', 'un', 'Logitech', null, 'mouse optico usb logitech', null, 0, now(), 'seed', now(), 'seed'),
  ('63000000-0000-4000-8000-000000000006', 'Papel A4 75 g, resma 500 folhas', null, '61000000-0000-4000-8000-000000000003', 'resma', 'Chamex', '461617', 'papel a4 75 g, resma 500 folhas chamex 461617', null, 0, now(), 'seed', now(), 'seed'),
  ('63000000-0000-4000-8000-000000000007', 'Caneta esferográfica azul', null, '61000000-0000-4000-8000-000000000003', 'cx', 'BIC', null, 'caneta esferografica azul bic', null, 0, now(), 'seed', now(), 'seed'),
  ('63000000-0000-4000-8000-000000000008', 'Banner lona 440 g', 'Impressão 4x0, com bastão e corda.', '61000000-0000-4000-8000-000000000004', 'm²', null, null, 'banner lona 440 g', null, 0, now(), 'seed', now(), 'seed'),
  ('63000000-0000-4000-8000-000000000009', 'Camisa polo bordada', null, '61000000-0000-4000-8000-000000000005', 'un', null, null, 'camisa polo bordada', '2026-10-01 12:00:00+00', 1, now(), 'seed', now(), 'seed')
on conflict do nothing;

-- Datas relativas a hoje: algumas cotações recentes, outras com mais de 90 dias (aparecem como antigas)
insert into product_offers (id, product_id, supplier_id, position, unit_price, quoted_on, lead_time_days, minimum_order)
values
  ('63100000-0000-4000-8000-000000000001', '63000000-0000-4000-8000-000000000001', '62000000-0000-4000-8000-000000000001', 1, 2.3500, current_date - 10, 5, 24),
  ('63100000-0000-4000-8000-000000000002', '63000000-0000-4000-8000-000000000001', '62000000-0000-4000-8000-000000000005', 2, 2.1000, current_date - 120, 15, 240),
  ('63100000-0000-4000-8000-000000000003', '63000000-0000-4000-8000-000000000001', '62000000-0000-4000-8000-000000000006', 3, 2.6000, current_date - 3, 3, null),
  ('63100000-0000-4000-8000-000000000004', '63000000-0000-4000-8000-000000000002', '62000000-0000-4000-8000-000000000001', 1, 3.9000, current_date - 10, 5, 12),
  ('63100000-0000-4000-8000-000000000005', '63000000-0000-4000-8000-000000000002', '62000000-0000-4000-8000-000000000006', 2, 3.7500, current_date - 30, 3, null),
  ('63100000-0000-4000-8000-000000000006', '63000000-0000-4000-8000-000000000003', '62000000-0000-4000-8000-000000000005', 1, 14.9000, current_date - 45, 15, 20),
  ('63100000-0000-4000-8000-000000000007', '63000000-0000-4000-8000-000000000004', '62000000-0000-4000-8000-000000000002', 1, 89.0000, current_date - 20, 7, null),
  ('63100000-0000-4000-8000-000000000008', '63000000-0000-4000-8000-000000000004', '62000000-0000-4000-8000-000000000003', 2, 79.5000, current_date - 100, 10, 10),
  ('63100000-0000-4000-8000-000000000009', '63000000-0000-4000-8000-000000000005', '62000000-0000-4000-8000-000000000002', 1, 32.0000, current_date - 5, 7, null),
  ('63100000-0000-4000-8000-000000000010', '63000000-0000-4000-8000-000000000006', '62000000-0000-4000-8000-000000000003', 1, 24.9000, current_date - 15, 10, 50),
  ('63100000-0000-4000-8000-000000000011', '63000000-0000-4000-8000-000000000006', '62000000-0000-4000-8000-000000000005', 2, 23.8000, current_date - 60, 15, 100),
  ('63100000-0000-4000-8000-000000000012', '63000000-0000-4000-8000-000000000006', '62000000-0000-4000-8000-000000000006', 3, 26.5000, current_date - 2, 2, null),
  ('63100000-0000-4000-8000-000000000013', '63000000-0000-4000-8000-000000000007', '62000000-0000-4000-8000-000000000003', 1, 38.0000, current_date - 15, 10, 5),
  ('63100000-0000-4000-8000-000000000014', '63000000-0000-4000-8000-000000000008', '62000000-0000-4000-8000-000000000004', 1, 45.0000, current_date - 8, 3, 1),
  ('63100000-0000-4000-8000-000000000015', '63000000-0000-4000-8000-000000000009', '62000000-0000-4000-8000-000000000007', 1, 29.0000, current_date - 200, 20, 50)
on conflict do nothing;
