package br.com.shema.crm.pricerecords.domain;

import br.com.shema.crm.pricerecords.domain.PriceRecordDrafts.CaronaDraft;
import br.com.shema.crm.pricerecords.domain.PriceRecordDrafts.ItemDraft;
import br.com.shema.crm.pricerecords.domain.PriceRecordDrafts.LineDraft;
import br.com.shema.crm.pricerecords.domain.PriceRecordDrafts.RecordDraft;
import br.com.shema.crm.shared.domain.BrFormat;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.domain.Money;
import br.com.shema.crm.shared.domain.Quantities;
import br.com.shema.crm.shared.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hibernate.annotations.BatchSize;

/**
 * Ata de registro de preços de uma licitação: vigência própria (independe do encerramento do contrato), itens
 * registrados e as caronas. As caronas ficam dentro do agregado porque os limites olham para todas de uma vez.
 *
 * <p>Limites de carona são configuráveis e só sinalizados (ADR 0014): {@link #checkCarona} devolve os avisos e
 * {@link #registerCarona} exige confirmação explícita para registrar mesmo assim. A única recusa firme é ata que não
 * permite adesão, porque isso está escrito no documento.
 */
@Entity
@Table(name = "price_records")
public class PriceRecord extends AuditableEntity {

    /** Um motivo para a carona precisar de confirmação. */
    public record LimitWarning(String code, String message) {}

    /** O que a carona registraria: valor calculado e os avisos. */
    public record CaronaCheck(BigDecimal value, List<LimitWarning> warnings) {}

    /**
     * Uso de um item pelas caronas.
     *
     * @param limit quanto as caronas podem somar no item (quantitativo × limite percentual)
     */
    public record ItemUsage(UUID itemId, BigDecimal registered, BigDecimal used, BigDecimal limit) {}

    /**
     * Quanto um órgão ainda pode aderir de um item (Fase 10): o menor entre o que sobra do limite por órgão e o que
     * sobra do limite total. Nunca negativo.
     *
     * @param agencyUsed quanto este órgão já aderiu do item
     */
    public record Allowance(UUID itemId, BigDecimal agencyUsed, BigDecimal maxQuantity) {}

    @Id
    private UUID id;

    @Column(name = "tender_id", nullable = false, updatable = false)
    private UUID tenderId;

    @Column(nullable = false, length = 60)
    private String number;

    @Column(name = "signed_at")
    private LocalDate signedAt;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "allows_adhesion", nullable = false)
    private boolean allowsAdhesion;

    @Column(length = 2000)
    private String notes;

    @Column(name = "document_id")
    private UUID documentId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "price_record_id", nullable = false)
    @OrderBy("position")
    @BatchSize(size = 50)
    private List<PriceRecordItem> items = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "price_record_id", nullable = false)
    @OrderBy("adheredAt, createdAt")
    @BatchSize(size = 50)
    private List<Carona> caronas = new ArrayList<>();

    protected PriceRecord() {}

    public PriceRecord(UUID tenderId, RecordDraft draft) {
        this.id = UUID.randomUUID();
        this.tenderId = tenderId;
        update(draft);
    }

    public void update(RecordDraft d) {
        if (d.number() == null || d.number().isBlank()) {
            throw new DomainRuleViolation("REQUIRED", "Informe o número da ata.");
        }
        if (d.number().trim().length() > 60) {
            throw new DomainRuleViolation("TOO_LONG", "Use no máximo 60 caracteres no número da ata.");
        }
        if (d.validUntil() == null) {
            throw new DomainRuleViolation("REQUIRED", "Informe até quando a ata vale.");
        }
        if (d.signedAt() != null && d.signedAt().isAfter(d.validUntil())) {
            throw new DomainRuleViolation(
                    "INVALID_VALIDITY", "A vigência não pode terminar antes da assinatura da ata.");
        }
        if (d.notes() != null && d.notes().length() > 2000) {
            throw new DomainRuleViolation("TOO_LONG", "Use no máximo 2000 caracteres nas observações.");
        }
        this.number = d.number().trim();
        this.signedAt = d.signedAt();
        this.validUntil = d.validUntil();
        this.allowsAdhesion = d.allowsAdhesion();
        this.notes = d.notes() == null || d.notes().isBlank() ? null : d.notes().trim();
        this.documentId = d.documentId();
    }

    /** Troca a lista de itens. Item com carona pode ser alterado (pelo id), mas não removido. */
    public void replaceItems(List<ItemDraft> drafts) {
        Map<UUID, PriceRecordItem> current =
                items.stream().collect(Collectors.toMap(PriceRecordItem::getId, Function.identity()));
        Set<UUID> used = new HashSet<>(usedQuantities().keySet());
        Set<UUID> kept = new HashSet<>();
        List<PriceRecordItem> result = new ArrayList<>();
        int position = 0;
        for (ItemDraft draft : drafts) {
            PriceRecordItem item;
            if (draft.id() == null) {
                item = new PriceRecordItem(position, draft);
            } else {
                item = current.get(draft.id());
                if (item == null || !kept.add(draft.id())) {
                    throw new DomainRuleViolation("ITEM_NOT_FOUND", "Item não encontrado nesta ata.");
                }
                item.apply(draft);
                item.moveTo(position);
            }
            result.add(item);
            position++;
        }
        for (PriceRecordItem old : items) {
            if (!kept.contains(old.getId()) && used.contains(old.getId())) {
                throw new DomainRuleViolation(
                        "ITEM_HAS_CARONAS", "\"" + old.getDescription() + "\" já tem carona e não pode ser removido.");
            }
        }
        items.clear();
        items.addAll(result);
    }

    /**
     * Confere a carona sem registrar: calcula o valor e lista o que passa dos limites.
     *
     * @param referenceValue valor da licitação, base do limite quando a ata não tem itens
     */
    public CaronaCheck checkCarona(CaronaDraft d, BigDecimal referenceValue, CaronaLimits limits) {
        if (!allowsAdhesion) {
            throw new DomainRuleViolation(
                    "ADHESION_NOT_ALLOWED", "Esta ata não permite adesão. Confira o documento da ata.");
        }
        if (d.agencyId() == null) {
            throw new DomainRuleViolation("REQUIRED", "Escolha o órgão que aderiu.");
        }
        if (d.adheredAt() == null) {
            throw new DomainRuleViolation("REQUIRED", "Informe a data da adesão.");
        }
        List<Carona.Line> lines = lines(d.lines());
        BigDecimal value = lines.isEmpty() ? valueWithoutLines(d.value()) : valueOf(lines);

        List<LimitWarning> warnings = new ArrayList<>();
        if (d.adheredAt().isAfter(validUntil)) {
            warnings.add(new LimitWarning(
                    "ATA_EXPIRED", "A ata venceu em " + BrDates.format(validUntil) + ", antes da data da adesão."));
        }
        Set<UUID> agencies = caronas.stream().map(Carona::getAgencyId).collect(Collectors.toSet());
        if (agencies.contains(d.agencyId())) {
            warnings.add(new LimitWarning(
                    "SAME_AGENCY", "Este órgão já tem carona nesta ata. As quantidades vão somar com a anterior."));
        } else if (agencies.size() + 1 > limits.maxAgencies()) {
            warnings.add(new LimitWarning(
                    "MAX_AGENCIES",
                    "A ata já tem carona de " + agencies.size() + " órgãos. O limite configurado é "
                            + limits.maxAgencies() + "."));
        }
        if (lines.isEmpty()) {
            BigDecimal limit =
                    percentOf(referenceValue, limits.maxTotalPercent()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal total = caronasValue().add(value);
            if (total.compareTo(limit) > 0) {
                warnings.add(new LimitWarning(
                        "VALUE_LIMIT",
                        "As caronas somariam " + BrFormat.money(total) + ", acima de " + limits.maxTotalPercent()
                                + "% do valor da ata (" + BrFormat.money(limit) + ")."));
            }
            BigDecimal agencyLimit =
                    percentOf(referenceValue, limits.maxPerAgencyPercent()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal agencyTotal = agencyValue(d.agencyId()).add(value);
            if (agencyTotal.compareTo(agencyLimit) > 0) {
                warnings.add(new LimitWarning(
                        "AGENCY_VALUE_LIMIT",
                        "Este órgão somaria " + BrFormat.money(agencyTotal) + ", acima de "
                                + limits.maxPerAgencyPercent() + "% do valor da ata por órgão ("
                                + BrFormat.money(agencyLimit) + ")."));
            }
        } else {
            Map<UUID, BigDecimal> used = usedQuantities();
            Map<UUID, BigDecimal> byAgency = usedQuantities(d.agencyId());
            for (Carona.Line line : lines) {
                PriceRecordItem item = item(line.itemId());
                BigDecimal limit = percentOf(item.getQuantity(), limits.maxTotalPercent());
                BigDecimal total =
                        used.getOrDefault(item.getId(), BigDecimal.ZERO).add(line.quantity());
                if (total.compareTo(limit) > 0) {
                    warnings.add(new LimitWarning(
                            "ITEM_LIMIT",
                            "\"" + item.getDescription() + "\": as caronas somariam " + BrFormat.quantity(total)
                                    + ", acima de " + limits.maxTotalPercent() + "% do registrado ("
                                    + BrFormat.quantity(limit) + ")."));
                }
                BigDecimal agencyLimit = percentOf(item.getQuantity(), limits.maxPerAgencyPercent());
                BigDecimal agencyTotal =
                        byAgency.getOrDefault(item.getId(), BigDecimal.ZERO).add(line.quantity());
                if (agencyTotal.compareTo(agencyLimit) > 0) {
                    warnings.add(new LimitWarning(
                            "AGENCY_ITEM_LIMIT",
                            "\"" + item.getDescription() + "\": este órgão somaria " + BrFormat.quantity(agencyTotal)
                                    + ", acima de " + limits.maxPerAgencyPercent() + "% do registrado por órgão ("
                                    + BrFormat.quantity(agencyLimit) + ")."));
                }
            }
        }
        return new CaronaCheck(value, warnings);
    }

    /**
     * Registra a carona. Com avisos, só registra se {@code confirmed}; nesse caso a carona fica marcada e guarda o
     * que foi confirmado.
     */
    public Carona registerCarona(
            CaronaDraft d,
            BigDecimal referenceValue,
            CaronaLimits limits,
            boolean confirmed,
            String byName,
            Instant at) {
        CaronaCheck check = checkCarona(d, referenceValue, limits);
        if (!check.warnings().isEmpty() && !confirmed) {
            throw new DomainRuleViolation(
                    "CARONA_LIMITS",
                    "Confira antes de registrar: "
                            + check.warnings().stream()
                                    .map(LimitWarning::message)
                                    .collect(Collectors.joining(" ")));
        }
        if (d.notes() != null && d.notes().length() > 2000) {
            throw new DomainRuleViolation("TOO_LONG", "Use no máximo 2000 caracteres nas observações.");
        }
        String overrideNote = check.warnings().isEmpty()
                ? null
                : truncate(
                        "Registrada mesmo assim por " + byName + ": "
                                + check.warnings().stream()
                                        .map(LimitWarning::message)
                                        .collect(Collectors.joining(" ")),
                        1000);
        Carona carona = new Carona(
                d.agencyId(),
                d.adheredAt(),
                check.value(),
                lines(d.lines()),
                d.dealId(),
                d.notes() == null || d.notes().isBlank() ? null : d.notes().trim(),
                overrideNote,
                byName,
                at);
        caronas.add(carona);
        return carona;
    }

    public Carona removeCarona(UUID caronaId) {
        Carona carona = caronas.stream()
                .filter(c -> c.getId().equals(caronaId))
                .findFirst()
                .orElseThrow(() -> new DomainRuleViolation("CARONA_NOT_FOUND", "Carona não encontrada nesta ata."));
        caronas.remove(carona);
        return carona;
    }

    public boolean isValidOn(LocalDate day) {
        return !validUntil.isBefore(day);
    }

    /** Vigente, permite adesão e ainda cabe outro órgão: dá para oferecer a carona. */
    public boolean isOpportunity(LocalDate today, CaronaLimits limits) {
        return isValidOn(today) && allowsAdhesion && agencyCount() < limits.maxAgencies();
    }

    public int agencyCount() {
        return (int) caronas.stream().map(Carona::getAgencyId).distinct().count();
    }

    public BigDecimal caronasValue() {
        return caronas.stream().map(Carona::getValue).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** Soma dos itens, ou nulo se a ata não tem itens (aí vale o valor da licitação). */
    public BigDecimal itemsValue() {
        return items.isEmpty()
                ? null
                : items.stream().map(PriceRecordItem::total).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public List<ItemUsage> usage(CaronaLimits limits) {
        Map<UUID, BigDecimal> used = usedQuantities();
        return items.stream()
                .map(i -> new ItemUsage(
                        i.getId(),
                        i.getQuantity(),
                        used.getOrDefault(i.getId(), BigDecimal.ZERO.setScale(3)),
                        percentOf(i.getQuantity(), limits.maxTotalPercent())))
                .toList();
    }

    /**
     * Quanto o órgão ainda pode aderir de cada item, na ordem da ata (Fase 10): base do kit de carona.
     */
    public List<Allowance> allowances(UUID agencyId, CaronaLimits limits) {
        Map<UUID, BigDecimal> used = usedQuantities();
        Map<UUID, BigDecimal> byAgency = usedQuantities(agencyId);
        return items.stream()
                .map(i -> {
                    BigDecimal agencyUsed = byAgency.getOrDefault(i.getId(), BigDecimal.ZERO.setScale(3));
                    BigDecimal agencyRoom = percentOf(i.getQuantity(), limits.maxPerAgencyPercent())
                            .subtract(agencyUsed);
                    BigDecimal totalRoom = percentOf(i.getQuantity(), limits.maxTotalPercent())
                            .subtract(used.getOrDefault(i.getId(), BigDecimal.ZERO));
                    BigDecimal max = agencyRoom.min(totalRoom).max(BigDecimal.ZERO);
                    return new Allowance(i.getId(), agencyUsed, max.setScale(3, RoundingMode.HALF_UP));
                })
                .toList();
    }

    /** Ata sem itens: quanto o órgão ainda pode aderir em valor. */
    public BigDecimal valueAllowance(UUID agencyId, BigDecimal referenceValue, CaronaLimits limits) {
        BigDecimal agencyRoom =
                percentOf(referenceValue, limits.maxPerAgencyPercent()).subtract(agencyValue(agencyId));
        BigDecimal totalRoom =
                percentOf(referenceValue, limits.maxTotalPercent()).subtract(caronasValue());
        return agencyRoom.min(totalRoom).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /** Se o órgão já tem carona nesta ata. */
    public boolean hasCaronaFrom(UUID agencyId) {
        return caronas.stream().anyMatch(c -> c.getAgencyId().equals(agencyId));
    }

    private Map<UUID, BigDecimal> usedQuantities() {
        return usedQuantities(null);
    }

    /** Quantidades aderidas por item; com {@code agencyId}, só as desse órgão. */
    private Map<UUID, BigDecimal> usedQuantities(UUID agencyId) {
        Map<UUID, BigDecimal> used = new HashMap<>();
        for (Carona c : caronas) {
            if (agencyId != null && !c.getAgencyId().equals(agencyId)) {
                continue;
            }
            for (Carona.Line l : c.getLines()) {
                used.merge(l.itemId(), l.quantity(), BigDecimal::add);
            }
        }
        return used;
    }

    private BigDecimal agencyValue(UUID agencyId) {
        return caronas.stream()
                .filter(c -> c.getAgencyId().equals(agencyId))
                .map(Carona::getValue)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** Junta linhas repetidas do mesmo item e confere que cada item é desta ata. */
    private List<Carona.Line> lines(List<LineDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        Map<UUID, BigDecimal> byItem = new LinkedHashMap<>();
        for (LineDraft l : drafts) {
            PriceRecordItem item = item(l.itemId());
            byItem.merge(item.getId(), Quantities.quantity(l.quantity(), "da carona"), BigDecimal::add);
        }
        return byItem.entrySet().stream()
                .map(e -> new Carona.Line(e.getKey(), e.getValue()))
                .toList();
    }

    private BigDecimal valueOf(List<Carona.Line> lines) {
        return lines.stream()
                .map(l -> Quantities.total(l.quantity(), item(l.itemId()).getUnitPrice()))
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private BigDecimal valueWithoutLines(BigDecimal value) {
        if (!items.isEmpty()) {
            throw new DomainRuleViolation(
                    "CARONA_LINES_REQUIRED", "Informe a quantidade de pelo menos um item da ata.");
        }
        if (value == null || value.signum() <= 0) {
            throw new DomainRuleViolation("REQUIRED", "Informe o valor da carona.");
        }
        return new Money(value).amount();
    }

    private PriceRecordItem item(UUID itemId) {
        return items.stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new DomainRuleViolation("ITEM_NOT_FOUND", "Item não encontrado nesta ata."));
    }

    private static BigDecimal percentOf(BigDecimal base, int percent) {
        return base.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenderId() {
        return tenderId;
    }

    public String getNumber() {
        return number;
    }

    public LocalDate getSignedAt() {
        return signedAt;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public boolean isAllowsAdhesion() {
        return allowsAdhesion;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public List<PriceRecordItem> getItems() {
        return List.copyOf(items);
    }

    public List<Carona> getCaronas() {
        return List.copyOf(caronas);
    }
}
