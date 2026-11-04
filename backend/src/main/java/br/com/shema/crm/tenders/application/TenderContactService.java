package br.com.shema.crm.tenders.application;

import br.com.shema.crm.shared.domain.DomainRuleViolation;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import br.com.shema.crm.tenders.TenderContacts;
import br.com.shema.crm.tenders.domain.Tender;
import br.com.shema.crm.tenders.domain.TenderContact;
import br.com.shema.crm.tenders.infrastructure.TenderContactRepository;
import br.com.shema.crm.tenders.infrastructure.TenderRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de contato com o órgão (Fase 9). Quem pode alterar a licitação registra e apaga (o próprio registro, ou
 * qualquer um sendo administrador); todos consultam, como o histórico.
 *
 * <p>O contrato citado vem da tela, que só oferece os contratos da licitação; o banco confere que ele existe.
 * Conferir que ele é desta licitação exigiria depender de {@code contracts}, que já depende de {@code tenders}.
 */
@Service
@Transactional
public class TenderContactService implements TenderContacts {

    /** Dados do contato para registrar. */
    public record ContactInput(
            UUID contractId,
            TenderContact.Channel channel,
            TenderContact.Outcome outcome,
            LocalDate contactedAt,
            String summary,
            boolean suggestedByAi) {}

    private final TenderContactRepository contacts;
    private final TenderRepository tenders;
    private final Clock clock;

    TenderContactService(TenderContactRepository contacts, TenderRepository tenders, Clock clock) {
        this.contacts = contacts;
        this.tenders = tenders;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TenderContact> list(UUID tenderId) {
        tender(tenderId);
        return contacts.findByTenderIdOrderByContactedAtDescCreatedAtDesc(tenderId);
    }

    @Transactional(readOnly = true)
    public boolean canEdit(UUID tenderId, CurrentUser user) {
        return user.canEditOwnedBy(tender(tenderId).getOwnerId());
    }

    public TenderContact register(UUID tenderId, ContactInput input) {
        CurrentUser user = CurrentUser.get();
        Tender tender = tender(tenderId);
        user.requireCanEditOwnedBy(tender.getOwnerId());
        TenderContact contact = TenderContact.register(
                tenderId,
                input.contractId(),
                input.channel(),
                input.outcome(),
                input.contactedAt(),
                input.summary(),
                input.suggestedByAi(),
                user.id(),
                user.name(),
                LocalDate.now(clock),
                clock.instant());
        return contacts.saveAndFlush(contact);
    }

    /** Apaga um contato registrado por engano: quem registrou ou um administrador. */
    public void remove(UUID tenderId, UUID contactId) {
        CurrentUser user = CurrentUser.get();
        Tender tender = tender(tenderId);
        user.requireCanEditOwnedBy(tender.getOwnerId());
        TenderContact contact = contacts.findById(contactId)
                .filter(c -> c.getTenderId().equals(tenderId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Contato não encontrado."));
        if (!user.isAdmin() && !contact.getUserId().equals(user.id())) {
            throw new DomainRuleViolation(
                    "CONTACT_NOT_YOURS", "Só quem registrou o contato, ou um administrador, pode apagá-lo.");
        }
        contacts.delete(contact);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactRef> recent(UUID tenderId, int limit) {
        return contacts.findByTenderIdOrderByContactedAtDescCreatedAtDesc(tenderId, Limit.of(limit)).stream()
                .map(c -> new ContactRef(
                        c.getId(),
                        c.getContactedAt(),
                        c.getChannel().label(),
                        c.getOutcome() == null ? null : c.getOutcome().label(),
                        c.getSummary(),
                        c.getContractId()))
                .toList();
    }

    private Tender tender(UUID tenderId) {
        return tenders.findById(tenderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Licitação não encontrada."));
    }
}
