package br.com.shema.crm.identity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** API pública do módulo de identidade para os outros módulos. */
public interface UserDirectory {

    Optional<UserSummary> find(UUID id);

    /** Nome, cargo e CPF de quem assina as propostas; vazio se o usuário não existe. */
    Optional<SignerProfile> signer(UUID id);

    /** Busca em lote, para listas não fazerem uma consulta por linha. */
    Map<UUID, UserSummary> findAll(Collection<UUID> ids);

    /** E-mail de um usuário ativo, para avisos internos; vazio se o usuário não existe ou está desativado. */
    Optional<String> activeEmail(UUID id);

    /** E-mails dos administradores ativos: recebem os avisos que não têm responsável. */
    List<String> activeAdminEmails();

    /** Usuários ativos cujo nome contém o texto (sem acento nem caixa), por nome: para o assistente achar "o Edwin". */
    List<UserSummary> searchActive(String text);
}
