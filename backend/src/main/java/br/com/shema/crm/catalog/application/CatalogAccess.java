package br.com.shema.crm.catalog.application;

import br.com.shema.crm.shared.persistence.AuditableEntity;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;

/**
 * Quem pode alterar o cadastro: Administrador e Comercial, qualquer registro (o cadastro é da equipe, não tem
 * responsável). Leitura só consulta.
 */
final class CatalogAccess {

    private CatalogAccess() {}

    static CurrentUser requireWriter() {
        CurrentUser me = CurrentUser.get();
        if (!canWrite(me)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Seu perfil só pode consultar fornecedores e produtos.");
        }
        return me;
    }

    static boolean canWrite() {
        return canWrite(CurrentUser.get());
    }

    private static boolean canWrite(CurrentUser me) {
        return me.isAdmin() || me.roles().contains("SALES");
    }

    /** Confere a versão que a tela tinha: se outra pessoa salvou antes, 409. */
    static <T extends AuditableEntity> T requireVersion(T entity, long version, String what) {
        if (entity.getVersion() == null || entity.getVersion() != version) {
            throw new ApiException(
                    ErrorCode.VERSION_CONFLICT,
                    "Outra pessoa alterou " + what + " enquanto você editava. Recarregue para ver a versão atual.");
        }
        return entity;
    }

    /** Termo de busca na mesma forma da coluna search_text, já escapado para LIKE. */
    static String likeTerm(String q) {
        String key = br.com.shema.crm.catalog.domain.CatalogText.key(q);
        return "%" + key.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
