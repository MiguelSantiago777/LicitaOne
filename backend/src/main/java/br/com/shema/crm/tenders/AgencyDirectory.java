package br.com.shema.crm.tenders;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** API pública dos órgãos para os outros módulos (negociações apontam para o órgão prospectado). */
public interface AgencyDirectory {

    Optional<AgencyRef> find(UUID agencyId);

    /** Busca em lote, para listas não fazerem uma consulta por linha. */
    Map<UUID, AgencyRef> findAll(Collection<UUID> agencyIds);

    /**
     * Prefeituras sem nenhuma licitação vencida pela Shema (em disputa e perdida não contam), por nome: os municípios
     * para prospectar caronas.
     *
     * @param state UF, ou nulo para todas
     */
    List<AgencyRef> municipalitiesWithoutWonTender(String state);

    /** E-mails dos contatos do órgão, na ordem do cadastro: destinatários dos rascunhos de follow-up. */
    List<String> contactEmails(UUID agencyId);

    /** Telefones dos contatos do órgão, na ordem do cadastro: para o "Abrir no WhatsApp" do follow-up. */
    List<String> contactPhones(UUID agencyId);

    /** Se o órgão é municipal: carona em ata municipal depende da lei local (Fase 10). */
    boolean isMunicipal(UUID agencyId);
}
