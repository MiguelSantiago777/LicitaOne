package br.com.shema.crm.notifications.application;

import br.com.shema.crm.contracts.ContractFollowUp;
import br.com.shema.crm.contracts.ContractFollowUp.ContractWithoutOrder;
import br.com.shema.crm.identity.UserDirectory;
import br.com.shema.crm.identity.UserSummary;
import br.com.shema.crm.notifications.AlertRaised;
import br.com.shema.crm.notifications.domain.Alert;
import br.com.shema.crm.notifications.domain.AlertRule;
import br.com.shema.crm.notifications.domain.AlertRules;
import br.com.shema.crm.notifications.domain.AlertRules.ContractFacts;
import br.com.shema.crm.notifications.domain.AlertRules.RecordFacts;
import br.com.shema.crm.notifications.domain.AlertRules.TenderFacts;
import br.com.shema.crm.notifications.domain.AlertSettings;
import br.com.shema.crm.notifications.infrastructure.AlertLog;
import br.com.shema.crm.pricerecords.PriceRecordCalendar;
import br.com.shema.crm.pricerecords.PriceRecordCalendar.ExpiringRecord;
import br.com.shema.crm.shared.messaging.EventMetadata;
import br.com.shema.crm.tenders.AgencyDirectory;
import br.com.shema.crm.tenders.TenderLifecycle;
import br.com.shema.crm.tenders.TenderRef;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uma passada do job de alertas: busca os candidatos nos outros módulos, aplica as regras e publica no outbox só os
 * alertas com chave nova. Não cria tarefa nem manda e-mail: isso é dos consumidores (seção 7).
 */
@Service
public class AlertScan {

    /** @param raised alertas novos por regra (os já avisados não contam) */
    public record Result(LocalDate day, Instant at, Map<AlertRule, Integer> raised) {

        public int total() {
            return raised.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private static final Logger log = LoggerFactory.getLogger(AlertScan.class);

    private final ContractFollowUp contracts;
    private final TenderLifecycle tenders;
    private final AgencyDirectory agencies;
    private final PriceRecordCalendar records;
    private final UserDirectory users;
    private final AlertLog alertLog;
    private final ApplicationEventPublisher events;
    private final AlertProperties properties;
    private final MeterRegistry meters;
    private final Clock clock;

    AlertScan(
            ContractFollowUp contracts,
            TenderLifecycle tenders,
            AgencyDirectory agencies,
            PriceRecordCalendar records,
            UserDirectory users,
            AlertLog alertLog,
            ApplicationEventPublisher events,
            AlertProperties properties,
            MeterRegistry meters,
            Clock clock) {
        this.contracts = contracts;
        this.tenders = tenders;
        this.agencies = agencies;
        this.records = records;
        this.users = users;
        this.alertLog = alertLog;
        this.events = events;
        this.properties = properties;
        this.meters = meters;
        this.clock = clock;
    }

    /** Os alertas e os eventos entram numa transação só: ou tudo fica gravado e publicado, ou nada. */
    @Transactional
    public Result run() {
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        AlertSettings settings = properties.settings();
        AlertRules rules = new AlertRules(settings, properties.appUrl());

        List<ContractWithoutOrder> withoutOrder =
                contracts.withoutOrderSignedOnOrBefore(today.minusDays(settings.contractWithoutOrderDays()));
        List<ExpiringRecord> expiring = records.validUntilBetween(today, today.plusDays(settings.widestExpiringBand()));
        List<TenderRef> awaiting =
                tenders.awaitingContractWonOnOrBefore(today.minusDays(settings.tenderWithoutContractDays()));

        Set<UUID> tenderIds = new HashSet<>();
        withoutOrder.forEach(c -> tenderIds.add(c.tenderId()));
        expiring.forEach(r -> tenderIds.add(r.tenderId()));
        Map<UUID, TenderRef> refs = new HashMap<>(tenders.findAll(tenderIds));
        awaiting.forEach(t -> refs.put(t.id(), t));
        Map<UUID, UserSummary> owners = users.findAll(refs.values().stream()
                .map(TenderRef::ownerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<UUID, TenderFacts> facts = new HashMap<>();
        refs.values().forEach(t -> facts.put(t.id(), facts(t, owners)));

        List<Alert> alerts = new ArrayList<>();
        Map<UUID, List<String>> emails = new HashMap<>();
        for (ContractWithoutOrder c : withoutOrder) {
            TenderFacts tender = facts.get(c.tenderId());
            if (tender != null) {
                List<String> to = emails.computeIfAbsent(refs.get(c.tenderId()).agencyId(), agencies::contactEmails);
                rules.contractWithoutOrder(
                                new ContractFacts(c.contractId(), c.kind(), c.number(), c.signedAt(), c.value()),
                                tender,
                                to,
                                today)
                        .ifPresent(alerts::add);
            }
        }
        for (ExpiringRecord r : expiring) {
            TenderFacts tender = facts.get(r.tenderId());
            if (tender != null) {
                rules.priceRecordExpiring(
                                new RecordFacts(r.id(), r.number(), r.validUntil(), r.allowsAdhesion()), tender, today)
                        .ifPresent(alerts::add);
            }
        }
        for (TenderRef t : awaiting) {
            rules.tenderWithoutContract(facts.get(t.id()), today).ifPresent(alerts::add);
        }

        Map<AlertRule, Integer> raised = new EnumMap<>(AlertRule.class);
        for (AlertRule rule : AlertRule.values()) {
            raised.put(rule, 0);
        }
        for (Alert alert : alerts) {
            alertLog.recordIfNew(alert, now).ifPresent(id -> {
                events.publishEvent(event(id, alert));
                raised.merge(alert.rule(), 1, Integer::sum);
                meters.counter("shema.alerts.raised", "rule", alert.rule().name())
                        .increment();
            });
        }
        Result result = new Result(today, now, raised);
        log.info("Alertas de {}: {} candidatos, {} novos {}", today, alerts.size(), result.total(), raised);
        return result;
    }

    private AlertRaised event(UUID id, Alert alert) {
        return new AlertRaised(
                EventMetadata.create(clock),
                id,
                alert.key(),
                alert.rule().name(),
                alert.tenderId(),
                alert.title(),
                alert.notes(),
                alert.dueOn(),
                alert.ownerId(),
                alert.draft() == null
                        ? null
                        : new AlertRaised.Draft(
                                alert.draft().to(),
                                alert.draft().subject(),
                                alert.draft().body()),
                alert.notice().subject(),
                alert.notice().body());
    }

    private static TenderFacts facts(TenderRef t, Map<UUID, UserSummary> owners) {
        UserSummary owner = t.ownerId() == null ? null : owners.get(t.ownerId());
        return new TenderFacts(
                t.id(),
                t.agencyName(),
                t.identification(),
                t.object(),
                t.totalValue(),
                t.wonAt(),
                t.ownerId(),
                owner == null ? null : owner.name());
    }
}
