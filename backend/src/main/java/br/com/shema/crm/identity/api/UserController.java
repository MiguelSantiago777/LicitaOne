package br.com.shema.crm.identity.api;

import br.com.shema.crm.identity.application.UserQueries;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Usuários")
class UserController {

    private final UserQueries userQueries;

    UserController(UserQueries userQueries) {
        this.userQueries = userQueries;
    }

    @GetMapping("/me")
    @Operation(summary = "Usuário logado", operationId = "getCurrentUser")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(userQueries.get(UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lista os usuários (só administradores)", operationId = "listUsers")
    List<UserResponse> list() {
        return userQueries.list().stream().map(UserResponse::from).toList();
    }
}
