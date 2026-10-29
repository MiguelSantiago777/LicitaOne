-- O ShedLock com a hora do banco (usingDbTime) grava em UTC com timezone('utc', now()), que devolve timestamp sem
-- fuso: numa coluna timestamptz, o PostgreSQL lia esse valor no fuso da sessão e a hora ficava deslocada (+3h).
-- A documentação do ShedLock pede timestamp sem fuso no PostgreSQL. Os locks em andamento são só descartados.
delete from shedlock;
alter table shedlock
    alter column lock_until type timestamp,
    alter column locked_at type timestamp;
