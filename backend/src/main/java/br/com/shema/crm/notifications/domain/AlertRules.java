package br.com.shema.crm.notifications.domain;

import br.com.shema.crm.shared.domain.BrFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * As três regras da seção 5.4, sem Spring: dado o fato e a data de hoje, decidem se há alerta e escrevem a tarefa, o
 * rascunho ao órgão e o aviso para a equipe. O job só busca os candidatos e publica o que sair daqui.
 */
public final class AlertRules {

    /** @param ownerName nome do responsável, para assinar o rascunho; nulo se a licitação não tem responsável */
    public record TenderFacts(
            UUID id,
            String agencyName,
            String identification,
            String object,
            BigDecimal totalValue,
            LocalDate wonAt,
            UUID ownerId,
            String ownerName) {}

    /** @param kind "Contrato", "Empenho" ou "Dispensa" */
    public record ContractFacts(UUID id, String kind, String number, LocalDate signedAt, BigDecimal value) {}

    public record RecordFacts(UUID id, String number, LocalDate validUntil, boolean allowsAdhesion) {}

    private final AlertSettings settings;
    private final String appUrl;

    /** @param appUrl endereço do LicitaOne, para o link nos e-mails internos */
    public AlertRules(AlertSettings settings, String appUrl) {
        this.settings = settings;
        this.appUrl = appUrl == null ? "" : appUrl.replaceAll("/+$", "");
    }

    /** Contrato assinado há N dias ou mais, ainda sem nenhum pedido: tarefa + rascunho ao órgão. */
    public Optional<Alert> contractWithoutOrder(
            ContractFacts contract, TenderFacts tender, List<String> agencyEmails, LocalDate today) {
        long days = ChronoUnit.DAYS.between(contract.signedAt(), today);
        if (days < settings.contractWithoutOrderDays()) {
            return Optional.empty();
        }
        String instrument = instrument(contract);
        String notes = instrument + " assinado em " + BrFormat.date(contract.signedAt()) + ", no valor de "
                + BrFormat.money(contract.value()) + ", ainda sem nenhum pedido. Mande o rascunho ao órgão"
                + " perguntando se há previsão de pedidos.";
        String body = String.join(
                "\n",
                "Prezados(as),",
                "",
                "Assinamos com " + tender.agencyName() + " o " + instrument.toLowerCase(Locale.ROOT) + " em "
                        + BrFormat.date(contract.signedAt()) + ", referente a " + tender.identification() + " ("
                        + tender.object() + ").",
                "",
                "Visto que participamos do certame, gostaríamos de saber se há previsão de pedidos de materiais,"
                        + " para agilizarmos a entrega.",
                "",
                "Ficamos à disposição.",
                "",
                "Atenciosamente,",
                signature(tender));
        Alert.Draft draft = new Alert.Draft(
                String.join(", ", agencyEmails),
                "Previsão de pedidos: " + instrument + " · " + tender.identification(),
                body);
        return Optional.of(new Alert(
                AlertRule.CONTRATO_SEM_PEDIDO.key(contract.id()),
                AlertRule.CONTRATO_SEM_PEDIDO,
                tender.id(),
                contract.id(),
                tender.agencyName(),
                title("Sem pedido há " + days + " dias", tender.agencyName()),
                notes,
                today,
                tender.ownerId(),
                draft,
                notice(
                        "Contrato sem pedido há " + days + " dias: " + tender.agencyName(),
                        tender,
                        instrument + " assinado em " + BrFormat.date(contract.signedAt()) + " e ainda sem pedido.",
                        "Saldo a pedir: " + BrFormat.money(contract.value()) + " (nenhum pedido registrado).",
                        "A tarefa com o rascunho de e-mail ao órgão já está no LicitaOne.")));
    }

    /**
     * Ata vencendo: um alerta por faixa (90, 60, 30 dias). Só a faixa mais próxima do vencimento sai, para a ata que
     * o job enxerga pela primeira vez já com 20 dias não gerar três tarefas de uma vez. A vigência entra na chave: se
     * a ata for prorrogada, as faixas recomeçam.
     */
    public Optional<Alert> priceRecordExpiring(RecordFacts record, TenderFacts tender, LocalDate today) {
        long daysLeft = ChronoUnit.DAYS.between(today, record.validUntil());
        if (daysLeft < 0) {
            return Optional.empty();
        }
        Integer band = settings.priceRecordExpiringDays().stream()
                .filter(b -> daysLeft <= b)
                .reduce((wider, narrower) -> narrower)
                .orElse(null);
        if (band == null) {
            return Optional.empty();
        }
        String when = daysLeft == 0 ? "vence hoje" : daysLeft == 1 ? "vence amanhã" : "vence em " + daysLeft + " dias";
        String validity = "A ata " + record.number() + " vale até " + BrFormat.date(record.validUntil()) + ".";
        String adhesion =
                record.allowsAdhesion() ? " Ela permite adesão: ofereça caronas a outros órgãos enquanto vale." : "";
        return Optional.of(new Alert(
                AlertRule.ATA_VENCENDO.key(record.id(), record.validUntil(), band),
                AlertRule.ATA_VENCENDO,
                tender.id(),
                record.id(),
                tender.agencyName(),
                title("Ata " + record.number() + " " + when, tender.agencyName()),
                validity + adhesion + " Confira no edital e com o órgão se cabe prorrogação.",
                today,
                tender.ownerId(),
                null,
                notice(
                        "Ata " + record.number() + " " + when + ": " + tender.agencyName(),
                        tender,
                        validity + adhesion,
                        null,
                        "A tarefa já está no LicitaOne.")));
    }

    /**
     * Licitação vencida há N dias ou mais sem contrato assinado: a tarefa já nasce atrasada (vence no dia em que o
     * prazo passou).
     */
    public Optional<Alert> tenderWithoutContract(TenderFacts tender, LocalDate today) {
        if (tender.wonAt() == null) {
            return Optional.empty();
        }
        long days = ChronoUnit.DAYS.between(tender.wonAt(), today);
        if (days < settings.tenderWithoutContractDays()) {
            return Optional.empty();
        }
        String fact = "Licitação vencida em " + BrFormat.date(tender.wonAt()) + " e ainda sem contrato assinado.";
        return Optional.of(new Alert(
                AlertRule.LICITACAO_SEM_CONTRATO.key(tender.id()),
                AlertRule.LICITACAO_SEM_CONTRATO,
                tender.id(),
                tender.id(),
                tender.agencyName(),
                title("Vencida há " + days + " dias sem contrato", tender.agencyName()),
                fact + " Cobre a assinatura do contrato (ou do empenho) com o órgão.",
                tender.wonAt().plusDays(settings.tenderWithoutContractDays()),
                tender.ownerId(),
                null,
                notice(
                        "Vencida há " + days + " dias sem contrato: " + tender.agencyName(),
                        tender,
                        fact,
                        "Valor a contratar: " + BrFormat.money(tender.totalValue()) + ".",
                        "A tarefa já está no LicitaOne.")));
    }

    private Alert.Notice notice(String subject, TenderFacts tender, String fact, String balance, String next) {
        StringBuilder body = new StringBuilder()
                .append(tender.agencyName())
                .append(" · ")
                .append(tender.identification())
                .append("\n\n")
                .append(fact)
                .append('\n');
        if (balance != null) {
            body.append(balance).append('\n');
        }
        body.append('\n').append(next).append('\n');
        if (!appUrl.isEmpty()) {
            body.append(appUrl).append("/licitacoes/").append(tender.id()).append('\n');
        }
        body.append("\nAviso automático do LicitaOne. Não responda este e-mail.");
        return new Alert.Notice("[LicitaOne] " + subject, body.toString());
    }

    private static String instrument(ContractFacts contract) {
        return contract.number() == null || contract.number().isBlank()
                ? contract.kind()
                : contract.kind() + " nº " + contract.number();
    }

    private static String signature(TenderFacts tender) {
        return tender.ownerName() == null ? "Shema Suprimentos" : tender.ownerName() + "\nShema Suprimentos";
    }

    /** O título da tarefa tem no máximo 200 caracteres. */
    private static String title(String what, String agencyName) {
        String title = what + ": " + agencyName;
        return title.length() <= 200 ? title : title.substring(0, 199) + "…";
    }
}
