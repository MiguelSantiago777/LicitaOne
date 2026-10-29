package br.com.shema.crm.tenders;

import br.com.shema.crm.shared.security.CurrentUser;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * API pública do módulo de licitações. O módulo de contratos avisa por aqui, na mesma transação, os fatos que
 * movem o ciclo de vida; a licitação decide se muda de estado e registra no histórico (ADR 0012). Quem fez a ação
 * vem explícito ({@code by}), porque também há chamadas fora de uma requisição (importação no consumidor).
 */
public interface TenderLifecycle {

    Optional<TenderRef> find(UUID tenderId);

    /** Busca em lote, para listas não fazerem uma consulta por linha. */
    Map<UUID, TenderRef> findAll(Collection<UUID> tenderIds);

    /** Licitações vencidas até a data que ainda esperam o contrato (alerta da seção 5.4), das mais antigas. */
    List<TenderRef> awaitingContractWonOnOrBefore(LocalDate wonOnOrBefore);

    /** Um contrato foi assinado. O primeiro leva a licitação para "Contrato assinado". */
    void contractSigned(UUID tenderId, LocalDate signedAt, CurrentUser by);

    /** Um pedido foi registrado. O primeiro leva a licitação para "Em andamento". */
    void orderRegistered(UUID tenderId, LocalDate orderedAt, CurrentUser by);

    /** Todos os contratos da licitação foram encerrados: a licitação também encerra. */
    void allContractsClosed(UUID tenderId, LocalDate closedAt, CurrentUser by);
}
