package br.com.shema.crm.identity.api;

import br.com.shema.crm.identity.application.UserAdmin;
import br.com.shema.crm.identity.application.UserQueries;
import br.com.shema.crm.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "users", description = "Usuários")
class UserController {

    private final UserQueries userQueries;
    private final UserAdmin userAdmin;

    UserController(UserQueries userQueries, UserAdmin userAdmin) {
        this.userQueries = userQueries;
        this.userAdmin = userAdmin;
    }

    @GetMapping("/me")
    @Operation(summary = "Usuário logado", operationId = "getCurrentUser")
    UserResponse me() {
        return UserResponse.from(userQueries.get(CurrentUser.get().id()));
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Troca a própria senha", operationId = "changeOwnPassword")
    void changeOwnPassword(@Valid @RequestBody UserRequests.ChangeOwnPassword request) {
        userAdmin.changeOwnPassword(CurrentUser.get().id(), request.currentPassword(), request.newPassword());
    }

    @GetMapping("/users/options")
    @Operation(summary = "Usuários ativos, para escolher responsável", operationId = "listUserOptions")
    List<UserOptionResponse> options() {
        return userQueries.activeSummaries().stream()
                .map(UserOptionResponse::from)
                .toList();
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lista os usuários (só administradores)", operationId = "listUsers")
    List<UserResponse> list() {
        return userQueries.list().stream().map(UserResponse::from).toList();
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um usuário", operationId = "createUser")
    UserResponse create(@Valid @RequestBody UserRequests.CreateUser request) {
        return UserResponse.from(userAdmin.create(request.name(), request.email(), request.role(), request.password()));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Altera nome, perfil ou situação de um usuário", operationId = "updateUser")
    UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserRequests.UpdateUser request) {
        return UserResponse.from(
                userAdmin.update(id, CurrentUser.get().id(), request.name(), request.role(), request.active()));
    }

    @PostMapping("/users/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Define uma nova senha para o usuário", operationId = "resetUserPassword")
    void resetPassword(@PathVariable UUID id, @Valid @RequestBody UserRequests.ResetPassword request) {
        userAdmin.resetPassword(id, request.password());
    }
}
