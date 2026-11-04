package br.com.shema.crm.tenders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** API pública dos contatos com o órgão (Fase 9): os mais recentes, para o follow-up escrito com IA. */
public interface TenderContacts {

    /**
     * @param channel rótulo do canal ("WhatsApp", "E-mail"...)
     * @param outcome rótulo da situação ("Pedido previsto"...), ou nulo se não foi classificado
     */
    record ContactRef(
            UUID id, LocalDate contactedAt, String channel, String outcome, String summary, UUID contractId) {}

    /** Os últimos contatos da licitação, do mais recente para o mais antigo. */
    List<ContactRef> recent(UUID tenderId, int limit);
}
