package br.com.shema.crm.identity.api;

import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        Role role,
        String roleLabel,
        String initials,
        boolean active,
        String jobTitle,
        String cpf,
        Instant lastLoginAt,
        long version) {

    static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getRole().label(),
                user.initials(),
                user.isActive(),
                user.getJobTitle(),
                user.getCpf(),
                user.getLastLoginAt(),
                user.getVersion() == null ? 0 : user.getVersion());
    }
}
