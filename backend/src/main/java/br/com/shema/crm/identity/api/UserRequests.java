package br.com.shema.crm.identity.api;

import br.com.shema.crm.identity.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class UserRequests {

    private UserRequests() {}

    record CreateUser(
            @NotBlank(message = "Informe o nome") @Size(max = 120)
            String name,

            @NotBlank(message = "Informe o e-mail") @Email(message = "E-mail inválido") @Size(max = 254)
            String email,

            @NotNull(message = "Escolha o perfil") Role role,

            @NotBlank(message = "Informe a senha inicial") @Size(max = 200)
            String password) {}

    record UpdateUser(
            @NotBlank(message = "Informe o nome") @Size(max = 120)
            String name,

            @NotNull(message = "Escolha o perfil") Role role,
            boolean active) {}

    record ResetPassword(
            @NotBlank(message = "Informe a nova senha") @Size(max = 200)
            String password) {}

    record ChangeOwnPassword(
            @NotBlank(message = "Informe a senha atual") String currentPassword,

            @NotBlank(message = "Informe a nova senha") @Size(max = 200)
            String newPassword) {}
}
