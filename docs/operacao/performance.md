# Performance: consultas, índices e volume

## Consultas por requisição (N+1)
`QueryCountTests` conta os comandos SQL de cada requisição (JPA e JdbcTemplate):
- **Listas paginadas** (licitações, tarefas, órgãos, alertas): uma página de 25 linhas não pode fazer mais consultas que uma de 3. Se alguém buscar o responsável, o órgão ou a negociação linha a linha, o teste quebra.
- **Telas sem paginação** têm um teto, medido em 29/10/2026 com o seed:

| Requisição | Consultas | Teto |
|---|---|---|
| `GET /tenders?size=25` (lista) | 2 | igual à página de 3 |
| `GET /deals/pipeline` (funil) | 7 | 8 |
| `GET /tenders/{id}/contracts` (itens, pedidos, saldo) | 6 | 7 |
| `GET /price-records?filter=TODAS` (atas com itens e caronas) | 4 | 5 |
| `GET /tenders/{id}/history`, `/contracts/without-orders`, `/price-records/prospects`, `/boards`, `/users` | 2 | 3 |
| `GET /tenders/summary`, `/tenders/top-agencies` | 1 | 2 |

Os vínculos (responsável, órgão, negociação, licitação) são buscados em lote, uma consulta por tipo (`findAll(ids)` nas APIs públicas dos módulos). As coleções dos agregados usam `@BatchSize` ou `@EntityGraph`.

## EXPLAIN com volume
`deploy/perf/volume-explain.sql` insere um volume de produção simulado e roda `EXPLAIN ANALYZE` nas consultas quentes. Tudo fica numa transação que termina em `ROLLBACK`.
- O volume: 2 mil órgãos, 50 mil licitações, 15 mil contratos abertos, 45 mil pedidos, 30 mil tarefas, 5 mil atas e 10 mil alertas.
- É mais de 10 vezes o que a planilha atual da Shema tem.

```bash
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < deploy/perf/volume-explain.sql
```

Resultado em 29/10/2026 (PostgreSQL 18, notebook de desenvolvimento):

| Consulta | Plano | Tempo |
|---|---|---|
| Lista de licitações por aba, maior valor primeiro | índice `ix_tenders_status` + top-N heapsort | 21 ms |
| Busca por texto (órgão, município, objeto, material, UASG, pregão) | seq scan (OR de 6 colunas) | 110 ms |
| Contadores das abas | seq scan + agregação | 22 ms |
| Contratos sem pedido há 30 dias | `ix_contracts_open` + anti-join | 24 ms |
| Licitações vencidas sem contrato (job diário) | `ix_tenders_status` | 77 ms |
| Saldo dos contratos abertos (painel) | agregação dos pedidos | 45 ms |
| Top órgãos por valor (painel) | seq scan + agregação | 35 ms |
| Tarefas abertas pelo vencimento | `ix_tasks_open_due` | 0,5 ms |
| Atas a vencer em 90 dias | `ix_price_records_valid_until` | 0,4 ms |
| Alertas mais recentes | `ix_alerts_raised` | 0,2 ms |
| Municípios sem licitação vencida | `ix_tenders_agency` (anti-join) | 2 ms |

**Conclusão:** nenhum índice novo foi necessário. Os índices das migrações cobrem os filtros e as ordenações. Mesmo com 10 vezes o volume esperado, a pior consulta fica em torno de 110 ms.

### Se o volume crescer muito
- **Busca por texto:** é a primeira a pesar, porque o OR entre seis colunas de duas tabelas impede o uso de índice. Daqui a uns 500 mil licitações, o caminho é uma coluna de busca (texto do órgão, do objeto e da identificação), com índice GIN `pg_trgm` ou `tsvector` mantido pela aplicação, e a especificação de busca passaria a usar só essa coluna. O índice trigram que existe hoje em `lower(object)` fica sem uso enquanto a busca for o OR.
- **Contadores e painel:** fazem agregação sobre a tabela inteira. Se ficarem lentos, dá para usar cache de um minuto (Caffeine, como prevê a seção 7) antes de pensar em tabela de resumo.

## Front
- As rotas são lazy, um bundle por tela.
- Os arquivos com hash têm cache de 1 ano no nginx, e o `index.html` sempre revalida.
- As listas são paginadas no servidor (no máximo 100 por página). O funil e as atas carregam tudo, porque o volume esperado é de dezenas a centenas de itens.
