-- Volume de produção simulado + EXPLAIN ANALYZE das consultas quentes (Fase 6, docs/operacao/performance.md).
-- Roda numa transação que termina em ROLLBACK: o banco fica como estava.
--   docker compose exec -T postgres psql -U shemacrm -d shemacrm -v ON_ERROR_STOP=1 < deploy/perf/volume-explain.sql
-- Volume: 2 mil órgãos, 50 mil licitações, 30 mil contratos, 60 mil pedidos, 30 mil tarefas, 5 mil atas e
-- 10 mil alertas (algumas vezes o que a Shema terá em anos de uso).

begin;

insert into agencies (id, name, sphere, city, state, created_at, updated_at)
select gen_random_uuid(), 'Órgão de volume ' || g, (array['MUNICIPAL','ESTADUAL','FEDERAL'])[1 + g % 3],
       'Cidade ' || (g % 400), (array['RJ','SP','MG','ES','BA'])[1 + g % 5], now(), now()
from generate_series(1, 2000) g;

create temp table vol_agencies as select id, row_number() over () as n from agencies where name like 'Órgão de volume %';
create temp table vol_users as select id, row_number() over () as n from users;

insert into tenders (id, agency_id, modality, process_number, object, material_type, total_value, status, won_at,
                     signed_at, orders_started_at, closed_at, owner_id, created_at, updated_at)
select gen_random_uuid(),
       (select id from vol_agencies where n = 1 + g % 2000),
       'PREGAO_ELETRONICO',
       lpad((g % 999)::text, 3, '0') || '/' || (2020 + g % 7),
       'Material de expediente lote ' || g,
       (array['Material gráfico','Expediente','Uniformes','Eletrodomésticos'])[1 + g % 4],
       (1000 + (g * 37) % 500000)::numeric(15, 2),
       s.status,
       case when s.status not in ('EM_DISPUTA','PERDIDA') then current_date - (g % 900) end,
       case when s.status in ('CONTRATO_ASSINADO','EM_ANDAMENTO','ENCERRADO') then current_date - (g % 800) end,
       case when s.status in ('EM_ANDAMENTO','ENCERRADO') then current_date - (g % 700) end,
       case when s.status = 'ENCERRADO' then current_date - (g % 100) end,
       (select id from vol_users where n = 1 + g % (select count(*) from vol_users)),
       now(), now()
from generate_series(1, 50000) g
cross join lateral (select (array['EM_DISPUTA','PERDIDA','VENCIDA','AGUARDANDO_ASSINATURA','CONTRATO_ASSINADO',
                                  'EM_ANDAMENTO','EM_ANDAMENTO','ENCERRADO','ENCERRADO','ENCERRADO'])[1 + g % 10]
                           as status) s;

insert into contracts (id, tender_id, kind, number, signed_at, closed_at, value, created_at, updated_at)
select gen_random_uuid(), t.id, 'CONTRATO', 'V' || row_number() over (), t.signed_at, t.closed_at, t.total_value,
       now(), now()
from tenders t
where t.object like 'Material de expediente lote %' and t.signed_at is not null;

insert into contract_orders (id, contract_id, ordered_at, value, created_at)
select gen_random_uuid(), c.id, c.signed_at + k, (c.value / 4)::numeric(15, 2), now()
from contracts c
cross join generate_series(1, 3) k
where c.number like 'V%' and c.id in (select id from contracts where number like 'V%' and closed_at is null
                                     order by id limit 20000);

insert into tasks (id, title, due_at, owner_id, tender_id, origin, alert_key, completed_at, created_at, updated_at)
select gen_random_uuid(), 'Tarefa de volume ' || g, now() + ((g % 120) - 60) * interval '1 day',
       (select id from vol_users where n = 1 + g % (select count(*) from vol_users)),
       null, 'MANUAL', null, case when g % 3 = 0 then now() end, now(), now()
from generate_series(1, 30000) g;

insert into price_records (id, tender_id, number, valid_until, allows_adhesion, created_at, updated_at)
select gen_random_uuid(), t.id, 'ARP V' || row_number() over (), current_date + ((row_number() over ())::int % 900 - 300),
       true, now(), now()
from (select id from tenders where object like 'Material de expediente lote %' and status = 'ENCERRADO' limit 5000) t;

insert into alerts (id, alert_key, rule, tender_id, subject_id, agency_name, title, due_on, raised_at)
select gen_random_uuid(), 'VOLUME:' || t.id, 'LICITACAO_SEM_CONTRATO', t.id, t.id, 'Órgão de volume',
       'Alerta de volume', current_date, now() - (row_number() over ())::int * interval '1 hour'
from (select id from tenders where object like 'Material de expediente lote %' limit 10000) t;

analyze;

\echo '== Lista de licitações (aba Em andamento, maior valor primeiro, 25 por página)'
explain (analyze, buffers, costs off)
select t.* from tenders t join agencies a on a.id = t.agency_id
where t.status in ('CONTRATO_ASSINADO', 'EM_ANDAMENTO')
order by t.total_value desc, t.id limit 25;

\echo '== Busca de licitações por texto (objeto ou órgão)'
explain (analyze, buffers, costs off)
select t.* from tenders t join agencies a on a.id = t.agency_id
where lower(t.object) like '%lote 4242%' or lower(a.name) like '%lote 4242%'
order by t.total_value desc, t.id limit 25;

\echo '== Contadores das abas (soma por situação)'
explain (analyze, buffers, costs off)
select status, count(*), sum(total_value) from tenders group by status;

\echo '== Contratos sem pedido há 30 dias (alerta e painel)'
explain (analyze, buffers, costs off)
select c.id from contracts c
where c.closed_at is null and c.signed_at <= current_date - 30
  and not exists (select 1 from contract_orders o where o.contract_id = c.id)
order by c.signed_at, c.created_at;

\echo '== Licitações vencidas sem contrato há 30 dias (alerta)'
explain (analyze, buffers, costs off)
select t.* from tenders t join agencies a on a.id = t.agency_id
where t.status in ('VENCIDA', 'AGUARDANDO_ASSINATURA') and t.won_at <= current_date - 30
order by t.won_at;

\echo '== Saldo dos contratos abertos (painel)'
explain (analyze, buffers, costs off)
select count(*), coalesce(sum(c.value), 0), coalesce(sum(o.ordered), 0)
from contracts c
left join (select contract_id, sum(value) as ordered from contract_orders group by contract_id) o on o.contract_id = c.id
where c.closed_at is null;

\echo '== Top órgãos por valor (painel)'
explain (analyze, buffers, costs off)
select a.id, a.name, count(*), sum(t.total_value) as value
from tenders t join agencies a on a.id = t.agency_id
where t.status not in ('EM_DISPUTA', 'PERDIDA')
group by a.id, a.name order by value desc limit 5;

\echo '== Tarefas abertas pelo vencimento (lista padrão)'
explain (analyze, buffers, costs off)
select * from tasks where completed_at is null order by due_at, id limit 25;

\echo '== Resumo das tarefas (abertas e atrasadas)'
explain (analyze, buffers, costs off)
select count(*), sum(case when due_at < now() then 1 else 0 end) from tasks where completed_at is null;

\echo '== Atas a vencer em 90 dias (alerta)'
explain (analyze, buffers, costs off)
select * from price_records where valid_until between current_date and current_date + 90 order by valid_until;

\echo '== Alertas mais recentes'
explain (analyze, buffers, costs off)
select * from alerts order by raised_at desc, title limit 25;

\echo '== Municípios sem licitação vencida (prospecção)'
explain (analyze, buffers, costs off)
select a.* from agencies a
where a.sphere = 'MUNICIPAL' and a.state = 'RJ'
  and not exists (select 1 from tenders t where t.agency_id = a.id and t.status not in ('EM_DISPUTA', 'PERDIDA'))
order by a.name;

rollback;
