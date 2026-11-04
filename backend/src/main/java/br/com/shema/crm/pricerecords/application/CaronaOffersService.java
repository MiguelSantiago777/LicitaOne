package br.com.shema.crm.pricerecords.application;

import br.com.shema.crm.pricerecords.CaronaOffers;
import br.com.shema.crm.pricerecords.domain.CaronaLimits;
import br.com.shema.crm.pricerecords.domain.PriceRecord;
import br.com.shema.crm.pricerecords.domain.PriceRecordItem;
import br.com.shema.crm.pricerecords.infrastructure.PriceRecordRepository;
import br.com.shema.crm.shared.domain.BrFormat;
import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import br.com.shema.crm.tenders.AgencyDirectory;
import br.com.shema.crm.tenders.AgencyRef;
import br.com.shema.crm.tenders.TenderLifecycle;
import br.com.shema.crm.tenders.TenderRef;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class CaronaOffersService implements CaronaOffers {

    /** Abaixo disso, a adesão pode não dar tempo: o município ainda precisa da autorização do gerenciador. */
    static final int SHORT_VALIDITY_DAYS = 30;

    private final PriceRecordRepository records;
    private final TenderLifecycle tenders;
    private final AgencyDirectory agencies;
    private final CaronaProperties properties;
    private final Clock clock;

    CaronaOffersService(
            PriceRecordRepository records,
            TenderLifecycle tenders,
            AgencyDirectory agencies,
            CaronaProperties properties,
            Clock clock) {
        this.records = records;
        this.tenders = tenders;
        this.agencies = agencies;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Offer prepare(UUID priceRecordId, UUID agencyId) {
        PriceRecord record = records.findById(priceRecordId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Ata não encontrada."));
        TenderRef tender = tenders.find(record.getTenderId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Licitação não encontrada."));
        if (agencyId == null) {
            throw new DomainRuleViolation("REQUIRED", "Escolha o município para oferecer a carona.");
        }
        AgencyRef candidate = agencies.find(agencyId)
                .orElseThrow(() -> new DomainRuleViolation("AGENCY_NOT_FOUND", "Órgão não encontrado."));
        AgencyRef manager = agencies.find(tender.agencyId()).orElse(null);
        LocalDate today = LocalDate.now(clock);
        if (candidate.id().equals(tender.agencyId())) {
            throw new DomainRuleViolation(
                    "OWN_AGENCY", "Este órgão é o gerenciador da ata; carona é para outro órgão.");
        }
        if (!record.isAllowsAdhesion()) {
            throw new DomainRuleViolation(
                    "ADHESION_NOT_ALLOWED", "Esta ata não permite adesão. Confira o documento da ata.");
        }
        if (!record.isValidOn(today)) {
            throw new DomainRuleViolation(
                    "ATA_EXPIRED", "A ata venceu em " + BrFormat.date(record.getValidUntil()) + ".");
        }

        CaronaLimits limits = properties.limits();
        Map<UUID, PriceRecordItem> byId =
                record.getItems().stream().collect(Collectors.toMap(PriceRecordItem::getId, Function.identity()));
        List<OfferItem> items = record.allowances(agencyId, limits).stream()
                .map(a -> {
                    PriceRecordItem i = byId.get(a.itemId());
                    return new OfferItem(
                            i.getId(),
                            i.getLot(),
                            i.getDescription(),
                            i.getUnit(),
                            i.getQuantity(),
                            i.getUnitPrice(),
                            a.agencyUsed(),
                            a.maxQuantity());
                })
                .toList();
        BigDecimal reference = tender.totalValue() == null ? BigDecimal.ZERO : tender.totalValue();
        BigDecimal maxValue = items.isEmpty() ? record.valueAllowance(agencyId, reference, limits) : null;
        long daysLeft = ChronoUnit.DAYS.between(today, record.getValidUntil());
        boolean municipal = agencies.isMunicipal(tender.agencyId());

        List<OfferWarning> warnings = new ArrayList<>();
        if (daysLeft < SHORT_VALIDITY_DAYS) {
            warnings.add(new OfferWarning(
                    "SHORT_VALIDITY",
                    "A ata vence em " + daysLeft + (daysLeft == 1 ? " dia" : " dias")
                            + ". O município ainda precisa da autorização do gerenciador: pode não dar tempo."));
        }
        boolean already = record.hasCaronaFrom(agencyId);
        if (already) {
            warnings.add(new OfferWarning(
                    "ALREADY_ADHERED",
                    candidate.name() + " já tem carona nesta ata. As quantidades abaixo descontam o que já aderiu."));
        } else if (record.agencyCount() >= limits.maxAgencies()) {
            warnings.add(new OfferWarning(
                    "MAX_AGENCIES",
                    "A ata já tem carona de " + record.agencyCount() + " órgãos. O limite configurado é "
                            + limits.maxAgencies() + "."));
        }
        boolean nothing = items.isEmpty()
                ? maxValue.signum() == 0
                : items.stream().allMatch(i -> i.maxQuantity().signum() == 0);
        if (nothing) {
            warnings.add(new OfferWarning(
                    "NO_BALANCE", "Não sobra quantidade para este município aderir nos limites configurados."));
        } else {
            long empty =
                    items.stream().filter(i -> i.maxQuantity().signum() == 0).count();
            if (empty > 0) {
                warnings.add(new OfferWarning(
                        "ITEMS_WITHOUT_BALANCE",
                        empty + (empty == 1 ? " item não tem" : " itens não têm")
                                + " quantidade para este município e ficam fora do kit."));
            }
        }
        if (municipal) {
            warnings.add(new OfferWarning(
                    "MUNICIPAL_MANAGER",
                    "O gerenciador é municipal. Confira se a lei de " + candidate.city()
                            + " permite carona em ata de outro município."));
        }
        return new Offer(
                record.getId(),
                tender.id(),
                record.getNumber(),
                record.getSignedAt(),
                record.getValidUntil(),
                daysLeft,
                tender.identification(),
                tender.object(),
                manager,
                municipal,
                candidate,
                items,
                maxValue,
                record.agencyCount(),
                limits.maxAgencies(),
                limits.maxPerAgencyPercent(),
                List.copyOf(warnings),
                CurrentUser.get().canEditOwnedBy(tender.ownerId()));
    }
}
