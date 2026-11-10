package br.com.shema.crm.assistant.application;

import br.com.shema.crm.assistant.application.AssistantModel.ToolSpec;
import br.com.shema.crm.assistant.domain.AssistantAnswer.QueryLog;
import br.com.shema.crm.assistant.domain.AssistantAnswer.Ref;
import br.com.shema.crm.contracts.ContractOrderContext;
import br.com.shema.crm.contracts.ContractOrderContext.OrderContext;
import br.com.shema.crm.deals.DealSearch;
import br.com.shema.crm.identity.UserDirectory;
import br.com.shema.crm.pricerecords.PriceRecordSearch;
import br.com.shema.crm.shared.domain.BrFormat;
import br.com.shema.crm.tasks.TaskSearch;
import br.com.shema.crm.tenders.AgencyDirectory;
import br.com.shema.crm.tenders.AgencyRef;
import br.com.shema.crm.tenders.TenderContacts;
import br.com.shema.crm.tenders.TenderLifecycle;
import br.com.shema.crm.tenders.TenderSearch;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * As consultas que o assistente pode fazer (ADR 0020). Todas só leem, pelas APIs públicas dos módulos, como o usuário
 * logado. Cada uma devolve no máximo {@link #MAX_ROWS} linhas em JSON compacto para o modelo, os itens que podem virar
 * link e o registro da consulta para "Ver as consultas feitas".
 */
@Component
public class AssistantTools {

    /** Linhas por consulta: o suficiente para responder, sem estourar o contexto do modelo. */
    public static final int MAX_ROWS = 50;

    /** O que uma consulta devolveu. */
    public record Output(String content, List<Ref> refs, QueryLog log, boolean error) {}

    /** Parâmetro inválido: a consulta volta como erro para o modelo corrigir. */
    static class BadArgument extends RuntimeException {
        BadArgument(String message) {
            super(message);
        }
    }

    public static final String ANSWER = "responder";

    private final TenderSearch tenders;
    private final TenderLifecycle tenderLookup;
    private final AgencyDirectory agencies;
    private final TenderContacts contacts;
    private final ContractOrderContext contracts;
    private final PriceRecordSearch records;
    private final TaskSearch tasks;
    private final DealSearch deals;
    private final UserDirectory users;
    private final JsonMapper json;
    private final Clock clock;

    AssistantTools(
            TenderSearch tenders,
            TenderLifecycle tenderLookup,
            AgencyDirectory agencies,
            TenderContacts contacts,
            ContractOrderContext contracts,
            PriceRecordSearch records,
            TaskSearch tasks,
            DealSearch deals,
            UserDirectory users,
            JsonMapper json,
            Clock clock) {
        this.tenders = tenders;
        this.tenderLookup = tenderLookup;
        this.agencies = agencies;
        this.contacts = contacts;
        this.contracts = contracts;
        this.records = records;
        this.tasks = tasks;
        this.deals = deals;
        this.users = users;
        this.json = json;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ especificações

    private static Map<String, Object> str(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> num(String description) {
        return Map.of("type", "number", "description", description);
    }

    private static Map<String, Object> integer(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private static Map<String, Object> list(String description, List<String> values) {
        return Map.of("type", "array", "description", description, "items", Map.of("type", "string", "enum", values));
    }

    private static Map<String, Object> props(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    public List<ToolSpec> specs() {
        return List.of(
                new ToolSpec(
                        "buscar_orgaos",
                        "Acha órgãos (prefeituras, secretarias...) pelo nome ou município. Use antes de filtrar por"
                                + " órgão, para ter o id.",
                        props("texto", str("Parte do nome ou do município, ex.: \"Magé\"")),
                        List.of("texto")),
                new ToolSpec(
                        "buscar_pessoas",
                        "Acha usuários ativos do LicitaOne pelo nome, para filtrar por responsável.",
                        props("texto", str("Parte do nome, ex.: \"Edwin\"")),
                        List.of("texto")),
                new ToolSpec(
                        "buscar_licitacoes",
                        "Licitações com filtros, das de maior valor. Situações: EM_DISPUTA, PERDIDA, VENCIDA,"
                                + " AGUARDANDO_ASSINATURA, CONTRATO_ASSINADO, EM_ANDAMENTO, ENCERRADO. O período vale"
                                + " na data que importa para a situação (vitória, assinatura, encerramento).",
                        props(
                                "situacoes",
                                list(
                                        "Situações; vazio para todas",
                                        List.of(
                                                "EM_DISPUTA",
                                                "PERDIDA",
                                                "VENCIDA",
                                                "AGUARDANDO_ASSINATURA",
                                                "CONTRATO_ASSINADO",
                                                "EM_ANDAMENTO",
                                                "ENCERRADO")),
                                "texto",
                                str("Busca no objeto, no órgão e no número"),
                                "orgao_id",
                                str("Id de um órgão (de buscar_orgaos)"),
                                "uf",
                                str("UF do órgão, ex.: RJ"),
                                "responsavel_id",
                                str("Id de um usuário (de buscar_pessoas)"),
                                "valor_minimo",
                                num("Valor total mínimo em reais"),
                                "de",
                                str("Início do período, AAAA-MM-DD"),
                                "ate",
                                str("Fim do período, AAAA-MM-DD")),
                        List.of()),
                new ToolSpec(
                        "contratos_abertos",
                        "Contratos e atas assinados ainda abertos, com valor, quanto já foi pedido, saldo a pedir,"
                                + " vigência e último pedido. Ordenados pelo maior saldo.",
                        props(
                                "saldo_minimo", num("Saldo a pedir mínimo em reais"),
                                "sem_pedido_ha_dias",
                                        integer("Só os sem pedido há pelo menos tantos dias (conta da assinatura se"
                                                + " nunca houve pedido)"),
                                "vigencia_ate", str("Só os que vencem até esta data, AAAA-MM-DD"),
                                "orgao", str("Parte do nome do órgão"),
                                "licitacao_id", str("Id de uma licitação")),
                        List.of()),
                new ToolSpec(
                        "detalhe_contrato",
                        "Itens de um contrato com quantidade, preço e saldo a pedir de cada um.",
                        props("contrato_id", str("Id do contrato (de contratos_abertos)")),
                        List.of("contrato_id")),
                new ToolSpec(
                        "buscar_atas",
                        "Atas de registro de preços. Filtro: VIGENTES, OPORTUNIDADES (vigente, permite carona e"
                                + " ainda cabe órgão), VENCENDO, VENCIDAS ou TODAS.",
                        props(
                                "filtro",
                                Map.of(
                                        "type",
                                        "string",
                                        "enum",
                                        List.of("VIGENTES", "OPORTUNIDADES", "VENCENDO", "VENCIDAS", "TODAS")),
                                "texto",
                                str("Busca no número, no órgão e no objeto"),
                                "vence_ate",
                                str("Só as que vencem até esta data, AAAA-MM-DD")),
                        List.of()),
                new ToolSpec(
                        "municipios_sem_ata",
                        "Prefeituras sem licitação vencida pela empresa e sem carona: onde oferecer as atas.",
                        props("uf", str("UF, ex.: RJ"), "texto", str("Parte do nome ou do município")),
                        List.of()),
                new ToolSpec(
                        "buscar_tarefas",
                        "Tarefas, das que vencem primeiro. Situação: ABERTAS (inclui atrasadas), ATRASADAS,"
                                + " CONCLUIDAS ou TODAS.",
                        props(
                                "situacao",
                                Map.of(
                                        "type",
                                        "string",
                                        "enum",
                                        List.of("ABERTAS", "ATRASADAS", "CONCLUIDAS", "TODAS")),
                                "responsavel_id",
                                str("Id de um usuário (de buscar_pessoas)"),
                                "licitacao_id",
                                str("Id de uma licitação"),
                                "vence_ate",
                                str("Só as que vencem até esta data, AAAA-MM-DD"),
                                "texto",
                                str("Busca no título")),
                        List.of()),
                new ToolSpec(
                        "buscar_negociacoes",
                        "Negociações do funil (prospecção de caronas), com etapa, valor e responsável.",
                        props(
                                "situacoes",
                                        list(
                                                "Situações; vazio para todas",
                                                List.of("EM_ANDAMENTO", "GANHA", "PERDIDA")),
                                "responsavel_id", str("Id de um usuário (de buscar_pessoas)"),
                                "orgao_id", str("Id de um órgão (de buscar_orgaos)"),
                                "texto", str("Busca no título, no órgão e na licitação")),
                        List.of()),
                new ToolSpec(
                        "contatos_recentes",
                        "Os últimos contatos registrados com o órgão de uma licitação (canal, data, situação e"
                                + " resumo).",
                        props("licitacao_id", str("Id da licitação")),
                        List.of("licitacao_id")),
                new ToolSpec(
                        ANSWER,
                        "Termina a pergunta com a resposta para a pessoa. Chame sempre, uma vez, no fim.",
                        props(
                                "texto",
                                str("A resposta em português, curta (até 6 linhas), com os números das consultas. Não"
                                        + " liste todos os itens no texto: a lista aparece embaixo."),
                                "itens",
                                Map.of(
                                        "type",
                                        "array",
                                        "description",
                                        "Ids (campo \"id\" das consultas) dos itens que respondem à pergunta, do mais"
                                                + " relevante para o menos; até 30",
                                        "items",
                                        Map.of("type", "string"))),
                        List.of("texto")));
    }

    /** Rótulo do passo mostrado enquanto a consulta roda. */
    public static String stepLabel(String tool) {
        return switch (tool) {
            case "buscar_orgaos" -> "Procurando o órgão…";
            case "buscar_pessoas" -> "Procurando a pessoa…";
            case "buscar_licitacoes" -> "Consultando licitações…";
            case "contratos_abertos" -> "Consultando contratos e saldos…";
            case "detalhe_contrato" -> "Abrindo os itens do contrato…";
            case "buscar_atas" -> "Consultando atas…";
            case "municipios_sem_ata" -> "Consultando municípios sem ata…";
            case "buscar_tarefas" -> "Consultando tarefas…";
            case "buscar_negociacoes" -> "Consultando o funil…";
            case "contatos_recentes" -> "Lendo os últimos contatos…";
            default -> "Consultando…";
        };
    }

    // ------------------------------------------------------------------ execução

    /** Roda uma consulta. Parâmetro inválido ou consulta desconhecida voltam como erro para o modelo. */
    public Output run(String tool, Map<String, Object> input) {
        Args a = new Args(input == null ? Map.of() : input);
        try {
            return switch (tool) {
                case "buscar_orgaos" -> agencies(a);
                case "buscar_pessoas" -> people(a);
                case "buscar_licitacoes" -> tenders(a);
                case "contratos_abertos" -> openContracts(a);
                case "detalhe_contrato" -> contract(a);
                case "buscar_atas" -> records(a);
                case "municipios_sem_ata" -> prospects(a);
                case "buscar_tarefas" -> tasks(a);
                case "buscar_negociacoes" -> deals(a);
                case "contatos_recentes" -> contacts(a);
                default -> error("Consulta desconhecida: " + tool + ".");
            };
        } catch (BadArgument e) {
            return error(e.getMessage());
        }
    }

    private Output error(String message) {
        return new Output(write(Map.of("erro", message)), List.of(), null, true);
    }

    private Output agencies(Args a) {
        String text = a.text("texto");
        List<AgencyRef> found = agencies.search(text, MAX_ROWS);
        List<Ref> refs = found.stream().map(AssistantTools::agencyRef).toList();
        return output(
                Map.of(
                        "orgaos",
                        found.stream()
                                .map(o -> row("id", o.id(), "nome", o.name(), "municipio", o.city(), "uf", o.state()))
                                .toList()),
                refs,
                new QueryLog("Órgãos", "nome ou município com \"" + text + "\"", found.size()));
    }

    private Output people(Args a) {
        String text = a.text("texto");
        var found = users.searchActive(text).stream().limit(MAX_ROWS).toList();
        return output(
                Map.of(
                        "pessoas",
                        found.stream()
                                .map(u -> row("id", u.id(), "nome", u.name()))
                                .toList()),
                List.of(),
                new QueryLog("Pessoas", "nome com \"" + text + "\"", found.size()));
    }

    private Output tenders(Args a) {
        TenderSearch.Query q = new TenderSearch.Query(
                a.strings("situacoes"),
                a.text("texto"),
                a.uuid("orgao_id"),
                a.state("uf"),
                a.uuid("responsavel_id"),
                a.decimal("valor_minimo"),
                a.date("de"),
                a.date("ate"));
        TenderSearch.Result result = tenders.search(q, MAX_ROWS);
        List<Ref> refs = result.rows().stream()
                .map(t -> new Ref(
                        "LICITACAO",
                        t.id().toString(),
                        t.agencyName() + " · " + t.identification(),
                        t.statusLabel() + " · " + BrFormat.money(t.totalValue()),
                        "/licitacoes/" + t.id(),
                        Map.of()))
                .toList();
        return output(
                Map.of(
                        "total",
                        result.total(),
                        "licitacoes",
                        result.rows().stream()
                                .map(t -> row(
                                        "id", t.id(),
                                        "orgao", t.agencyName(),
                                        "municipio", t.city() + "/" + t.state(),
                                        "numero", t.identification(),
                                        "objeto", t.object(),
                                        "situacao", t.statusLabel(),
                                        "valor", t.totalValue(),
                                        "vitoria", t.wonAt()))
                                .toList()),
                refs,
                new QueryLog(
                        "Licitações",
                        filters(
                                q.statuses() == null || q.statuses().isEmpty()
                                        ? null
                                        : "situação " + String.join(", ", q.statuses()),
                                q.text() == null ? null : "texto \"" + q.text() + "\"",
                                q.agencyId() == null ? null : "órgão escolhido",
                                q.state() == null ? null : "UF " + q.state(),
                                q.ownerId() == null ? null : "responsável escolhido",
                                q.minValue() == null ? null : "valor a partir de " + BrFormat.money(q.minValue()),
                                q.from() == null ? null : "de " + BrFormat.date(q.from()),
                                q.to() == null ? null : "até " + BrFormat.date(q.to())),
                        (int) Math.min(result.total(), Integer.MAX_VALUE)),
                result.total() > result.rows().size()
                        ? "Mostrando " + result.rows().size() + " de " + result.total() + " licitações, as de maior"
                                + " valor."
                        : null);
    }

    private Output openContracts(Args a) {
        BigDecimal minRemaining = a.decimal("saldo_minimo");
        Integer days = a.integer("sem_pedido_ha_dias");
        LocalDate until = a.date("vigencia_ate");
        String agency = a.text("orgao");
        UUID tenderId = a.uuid("licitacao_id");
        LocalDate today = LocalDate.now(clock);
        List<OrderContext> matching = contracts.allOpen().stream()
                .filter(c -> minRemaining == null || c.remaining().compareTo(minRemaining) >= 0)
                .filter(c -> days == null || daysWithoutOrder(c, today) >= days)
                .filter(c -> until == null
                        || (c.validUntil() != null && !c.validUntil().isAfter(until)))
                .filter(c -> agency == null || plain(c.agencyName()).contains(plain(agency)))
                .filter(c -> tenderId == null || c.tenderId().equals(tenderId))
                .sorted(Comparator.comparing(OrderContext::remaining).reversed())
                .toList();
        List<OrderContext> shown = matching.stream().limit(MAX_ROWS).toList();
        List<Ref> refs = shown.stream().map(AssistantTools::contractRef).toList();
        return output(
                Map.of(
                        "total",
                        matching.size(),
                        "contratos",
                        shown.stream()
                                .map(c -> row(
                                        "id", c.contractId(),
                                        "licitacao_id", c.tenderId(),
                                        "contrato", c.label(),
                                        "assinado_em", c.signedAt(),
                                        "vigente_ate", c.validUntil(),
                                        "valor", c.value(),
                                        "pedido", c.ordered(),
                                        "saldo", c.remaining(),
                                        "ultimo_pedido", c.lastOrderAt(),
                                        "dias_sem_pedido", daysWithoutOrder(c, today)))
                                .toList()),
                refs,
                new QueryLog(
                        "Contratos abertos",
                        filters(
                                minRemaining == null ? null : "saldo a partir de " + BrFormat.money(minRemaining),
                                days == null ? null : "sem pedido há " + days + " dias ou mais",
                                until == null ? null : "vencem até " + BrFormat.date(until),
                                agency == null ? null : "órgão com \"" + agency + "\"",
                                tenderId == null ? null : "da licitação escolhida"),
                        matching.size()),
                matching.size() > shown.size()
                        ? "Mostrando " + shown.size() + " de " + matching.size() + " contratos, os de maior saldo."
                        : null);
    }

    private Output contract(Args a) {
        UUID id = a.uuid("contrato_id");
        if (id == null) {
            throw new BadArgument("Informe contrato_id.");
        }
        OrderContext c = contracts.find(id).orElseThrow(() -> new BadArgument("Contrato não encontrado."));
        return output(
                Map.of(
                        "id",
                        c.contractId(),
                        "contrato",
                        c.label(),
                        "encerrado",
                        c.closed(),
                        "saldo",
                        c.remaining(),
                        "ultimo_pedido",
                        c.lastOrderAt() == null ? "nenhum" : c.lastOrderAt().toString(),
                        "itens",
                        c.items().stream()
                                .limit(MAX_ROWS)
                                .map(i -> row(
                                        "item", i.description(),
                                        "unidade", i.unit(),
                                        "quantidade", i.quantity(),
                                        "preco", i.unitPrice(),
                                        "saldo_quantidade", i.remaining()))
                                .toList()),
                List.of(contractRef(c)),
                new QueryLog("Itens do contrato", c.label(), c.items().size()));
    }

    private Output records(Args a) {
        String filter = a.text("filtro");
        String text = a.text("texto");
        LocalDate until = a.date("vence_ate");
        List<PriceRecordSearch.Row> matching = records.search(filter == null ? "VIGENTES" : filter, text).stream()
                .filter(r -> until == null || !r.validUntil().isAfter(until))
                .toList();
        List<PriceRecordSearch.Row> shown = matching.stream().limit(MAX_ROWS).toList();
        List<Ref> refs = shown.stream()
                .filter(r -> r.tenderId() != null)
                .map(r -> new Ref(
                        "ATA",
                        r.id().toString(),
                        "Ata " + r.number() + (r.agencyName() == null ? "" : " · " + r.agencyName()),
                        "vale até " + BrFormat.date(r.validUntil())
                                + (r.allowsAdhesion() ? " · " + r.agencyCount() + " carona(s)" : " · sem adesão"),
                        "/licitacoes/" + r.tenderId(),
                        Map.of("aba", "documentos")))
                .toList();
        return output(
                Map.of(
                        "total",
                        matching.size(),
                        "atas",
                        shown.stream()
                                .map(r -> row(
                                        "id", r.id(),
                                        "numero", r.number(),
                                        "orgao", r.agencyName(),
                                        "objeto", r.object(),
                                        "vale_ate", r.validUntil(),
                                        "dias_para_vencer", r.daysLeft(),
                                        "situacao", r.situation(),
                                        "permite_carona", r.allowsAdhesion(),
                                        "oportunidade", r.opportunity(),
                                        "caronas", r.agencyCount(),
                                        "usado_pelas_caronas_percentual", r.usedPercent()))
                                .toList()),
                refs,
                new QueryLog(
                        "Atas",
                        filters(
                                "filtro " + (filter == null ? "VIGENTES" : filter),
                                text == null ? null : "texto \"" + text + "\"",
                                until == null ? null : "vencem até " + BrFormat.date(until)),
                        matching.size()),
                matching.size() > shown.size()
                        ? "Mostrando " + shown.size() + " de " + matching.size() + " atas."
                        : null);
    }

    private Output prospects(Args a) {
        String state = a.state("uf");
        String text = a.text("texto");
        List<AgencyRef> matching = records.prospects(state, text);
        List<AgencyRef> shown = matching.stream().limit(MAX_ROWS).toList();
        return output(
                Map.of(
                        "total",
                        matching.size(),
                        "municipios",
                        shown.stream()
                                .map(o -> row("id", o.id(), "nome", o.name(), "municipio", o.city(), "uf", o.state()))
                                .toList()),
                shown.stream().map(AssistantTools::agencyRef).toList(),
                new QueryLog(
                        "Municípios sem ata",
                        filters(state == null ? null : "UF " + state, text == null ? null : "texto \"" + text + "\""),
                        matching.size()),
                matching.size() > shown.size()
                        ? "Mostrando " + shown.size() + " de " + matching.size() + " municípios."
                        : null);
    }

    private Output tasks(Args a) {
        String situation = a.text("situacao");
        LocalDate until = a.date("vence_ate");
        TaskSearch.Query q = new TaskSearch.Query(
                situation,
                a.uuid("responsavel_id"),
                a.uuid("licitacao_id"),
                until == null
                        ? null
                        : until.atTime(LocalTime.MAX).atZone(clock.getZone()).toOffsetDateTime(),
                a.text("texto"));
        TaskSearch.Result result = tasks.search(q, MAX_ROWS);
        List<Ref> refs = result.rows().stream()
                .map(t -> new Ref(
                        "TAREFA",
                        t.id().toString(),
                        t.title(),
                        statusLabel(t.status())
                                + (t.dueAt() == null ? "" : " · vence " + BrFormat.date(local(t.dueAt())))
                                + (t.ownerName() == null ? "" : " · " + t.ownerName()),
                        t.tenderId() == null ? "/tarefas" : "/licitacoes/" + t.tenderId(),
                        t.tenderId() == null ? Map.of("q", t.title()) : Map.of("aba", "tarefas")))
                .toList();
        return output(
                Map.of(
                        "total",
                        result.total(),
                        "tarefas",
                        result.rows().stream()
                                .map(t -> row(
                                        "id", t.id(),
                                        "titulo", t.title(),
                                        "vence_em", t.dueAt() == null ? null : local(t.dueAt()),
                                        "situacao", t.status(),
                                        "responsavel", t.ownerName(),
                                        "licitacao", t.tenderLabel(),
                                        "negociacao", t.dealTitle()))
                                .toList()),
                refs,
                new QueryLog(
                        "Tarefas",
                        filters(
                                "situação " + (situation == null ? "ABERTAS" : situation),
                                q.ownerId() == null ? null : "responsável escolhido",
                                q.tenderId() == null ? null : "da licitação escolhida",
                                until == null ? null : "vencem até " + BrFormat.date(until),
                                q.text() == null ? null : "texto \"" + q.text() + "\""),
                        (int) result.total()),
                result.total() > result.rows().size()
                        ? "Mostrando " + result.rows().size() + " de " + result.total() + " tarefas, as que vencem"
                                + " primeiro."
                        : null);
    }

    private Output deals(Args a) {
        DealSearch.Query q = new DealSearch.Query(
                a.strings("situacoes"), a.uuid("responsavel_id"), a.uuid("orgao_id"), a.text("texto"));
        List<DealSearch.Row> matching = deals.search(q);
        List<DealSearch.Row> shown = matching.stream().limit(MAX_ROWS).toList();
        List<Ref> refs = shown.stream()
                .map(d -> new Ref(
                        "NEGOCIACAO",
                        d.id().toString(),
                        d.title(),
                        d.stageName() + " · " + BrFormat.money(d.value()),
                        "/negociacoes",
                        Map.of("q", d.title())))
                .toList();
        return output(
                Map.of(
                        "total",
                        matching.size(),
                        "negociacoes",
                        shown.stream()
                                .map(d -> row(
                                        "id", d.id(),
                                        "titulo", d.title(),
                                        "etapa", d.stageName(),
                                        "situacao", d.status(),
                                        "valor", d.value(),
                                        "responsavel", d.ownerName(),
                                        "orgao", d.agencyName(),
                                        "tarefas_abertas", d.openTasks(),
                                        "tem_tarefa_atrasada", d.hasOverdueTask(),
                                        "fechada_em", d.closedAt()))
                                .toList()),
                refs,
                new QueryLog(
                        "Negociações",
                        filters(
                                q.statuses() == null || q.statuses().isEmpty()
                                        ? null
                                        : "situação " + String.join(", ", q.statuses()),
                                q.ownerId() == null ? null : "responsável escolhido",
                                q.agencyId() == null ? null : "órgão escolhido",
                                q.text() == null ? null : "texto \"" + q.text() + "\""),
                        matching.size()),
                matching.size() > shown.size()
                        ? "Mostrando " + shown.size() + " de " + matching.size() + " negociações."
                        : null);
    }

    private Output contacts(Args a) {
        UUID tenderId = a.uuid("licitacao_id");
        if (tenderId == null) {
            throw new BadArgument("Informe licitacao_id.");
        }
        var tender = tenderLookup.find(tenderId).orElseThrow(() -> new BadArgument("Licitação não encontrada."));
        var recent = contacts.recent(tenderId, 10);
        return output(
                Map.of(
                        "licitacao_id",
                        tenderId,
                        "orgao",
                        tender.agencyName(),
                        "contatos",
                        recent.stream()
                                .map(c -> row(
                                        "data", c.contactedAt(),
                                        "canal", c.channel(),
                                        "situacao", c.outcome(),
                                        "resumo", c.summary()))
                                .toList()),
                List.of(new Ref(
                        "LICITACAO",
                        tenderId.toString(),
                        tender.agencyName() + " · " + tender.identification(),
                        recent.isEmpty() ? "sem contatos" : recent.size() + " contato(s)",
                        "/licitacoes/" + tenderId,
                        Map.of("aba", "historico"))),
                new QueryLog(
                        "Contatos recentes", tender.agencyName() + " · " + tender.identification(), recent.size()));
    }

    // ------------------------------------------------------------------ apoio

    private Output output(Map<String, Object> body, List<Ref> refs, QueryLog log) {
        return output(body, refs, log, null);
    }

    private Output output(Map<String, Object> body, List<Ref> refs, QueryLog log, String note) {
        Map<String, Object> content = new LinkedHashMap<>(body);
        if (note != null) {
            content.put("aviso", note);
        }
        return new Output(write(content), refs, log, false);
    }

    private String write(Object value) {
        return json.writeValueAsString(value);
    }

    /** Linha com os valores já em texto simples: datas ISO, números sem notação científica, nulos fora. */
    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            Object v = pairs[i + 1];
            if (v == null) {
                continue;
            }
            map.put(
                    (String) pairs[i],
                    v instanceof BigDecimal d
                            ? d.stripTrailingZeros().toPlainString()
                            : v instanceof UUID || v instanceof LocalDate ? v.toString() : v);
        }
        return map;
    }

    private static String filters(String... parts) {
        List<String> present = new ArrayList<>();
        for (String p : parts) {
            if (p != null) {
                present.add(p);
            }
        }
        return present.isEmpty() ? "sem filtro" : String.join(" · ", present);
    }

    private static Ref agencyRef(AgencyRef o) {
        return new Ref(
                "ORGAO",
                o.id().toString(),
                o.name(),
                o.city() == null ? null : o.city() + (o.state() == null ? "" : "/" + o.state()),
                "/orgaos",
                Map.of("q", o.name()));
    }

    private static Ref contractRef(OrderContext c) {
        return new Ref(
                "CONTRATO",
                c.contractId().toString(),
                c.label(),
                "saldo " + BrFormat.money(c.remaining())
                        + (c.lastOrderAt() == null
                                ? " · sem pedido"
                                : " · último pedido " + BrFormat.date(c.lastOrderAt())),
                "/licitacoes/" + c.tenderId(),
                Map.of("aba", "itens"));
    }

    private static long daysWithoutOrder(OrderContext c, LocalDate today) {
        LocalDate since = c.lastOrderAt() != null ? c.lastOrderAt() : c.signedAt();
        return since == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(since, today));
    }

    private LocalDate local(OffsetDateTime at) {
        return at.atZoneSameInstant(clock.getZone()).toLocalDate();
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "ATRASADA" -> "Atrasada";
            case "CONCLUIDA" -> "Concluída";
            default -> "Aberta";
        };
    }

    static String plain(String text) {
        return text == null
                ? ""
                : Normalizer.normalize(text.strip(), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .toLowerCase(Locale.ROOT);
    }

    /** Leitura tolerante dos parâmetros que o modelo mandou; valor inválido vira erro claro para ele corrigir. */
    static final class Args {

        private final Map<String, Object> input;

        Args(Map<String, Object> input) {
            this.input = input;
        }

        String text(String key) {
            Object v = input.get(key);
            if (v == null) {
                return null;
            }
            String s = v.toString().strip();
            return s.isEmpty() ? null : s.length() > 200 ? s.substring(0, 200) : s;
        }

        String state(String key) {
            String s = text(key);
            return s == null ? null : s.toUpperCase(Locale.ROOT);
        }

        UUID uuid(String key) {
            String s = text(key);
            if (s == null) {
                return null;
            }
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                throw new BadArgument(key + " não é um id válido. Use o campo \"id\" de uma consulta.");
            }
        }

        BigDecimal decimal(String key) {
            Object v = input.get(key);
            if (v == null || v.toString().isBlank()) {
                return null;
            }
            try {
                return new BigDecimal(v.toString().strip());
            } catch (NumberFormatException e) {
                throw new BadArgument(key + " deve ser um número, em reais, sem R$ nem pontos de milhar.");
            }
        }

        Integer integer(String key) {
            BigDecimal d = decimal(key);
            return d == null ? null : d.intValue();
        }

        LocalDate date(String key) {
            String s = text(key);
            if (s == null) {
                return null;
            }
            try {
                return LocalDate.parse(s);
            } catch (DateTimeParseException e) {
                throw new BadArgument(key + " deve ser uma data AAAA-MM-DD.");
            }
        }

        Set<String> strings(String key) {
            Object v = input.get(key);
            if (!(v instanceof Collection<?> c) || c.isEmpty()) {
                return null;
            }
            Set<String> out = new LinkedHashSet<>();
            c.forEach(o -> {
                if (o != null && !o.toString().isBlank()) {
                    out.add(o.toString().strip().toUpperCase(Locale.ROOT));
                }
            });
            return out.isEmpty() ? null : out;
        }
    }
}
