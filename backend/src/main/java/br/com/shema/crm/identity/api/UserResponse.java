package br.com.shema.crm.identity.api;

import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, Role role, String roleLabel, String initials) {

    static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getRole().label(),
                user.initials());
    }
}
