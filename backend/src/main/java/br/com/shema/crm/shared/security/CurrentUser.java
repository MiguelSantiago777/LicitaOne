package br.com.shema.crm.shared.security;

import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Quem está fazendo a requisição, lido do JWT validado. */
public record CurrentUser(UUID id, String name, List<String> roles) {

    public static CurrentUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("name"), roles == null ? List.of() : roles);
    }

    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }

    /** ADMIN edita tudo; COMERCIAL só o que é seu (ou o que ainda não tem responsável); LEITURA nunca escreve. */
    public boolean canEditOwnedBy(UUID ownerId) {
        if (isAdmin()) {
            return true;
        }
        return roles.contains("SALES") && (ownerId == null || ownerId.equals(id));
    }

    public void requireCanEditOwnedBy(UUID ownerId) {
        if (!canEditOwnedBy(ownerId)) {
            throw new ApiException(
                    ErrorCode.ACCESS_DENIED, "Só o responsável ou um administrador pode alterar este registro.");
        }
    }
}
