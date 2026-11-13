package br.com.shema.crm.identity.application;

import br.com.shema.crm.identity.domain.PasswordPolicy;
import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.RefreshTokenRepository;
import br.com.shema.crm.identity.infrastructure.UserRepository;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro de usuários pelo administrador e troca de senha. */
@Service
@Transactional
public class UserAdmin {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserAdmin(
            UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public User create(String name, String email, Role role, String password) {
        requireStrong(password, email);
        if (users.findByEmail(User.normalizeEmail(email)).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "Já existe um usuário com este e-mail.");
        }
        return users.save(new User(name, email, passwordEncoder.encode(password), role));
    }

    public User update(
            UUID id, UUID actingUserId, String name, Role role, boolean active, String jobTitle, String cpf) {
        User user = get(id);
        if (id.equals(actingUserId) && (!active || role != Role.ADMIN)) {
            throw new ApiException(ErrorCode.CONFLICT, "Você não pode tirar o próprio acesso de administrador.");
        }
        user.changeProfile(name, role, active);
        user.changeSignerData(jobTitle, cpf);
        if (!active) {
            refreshTokens.revokeAllForUser(id, clock.instant());
        }
        return user;
    }

    /** Cargo e CPF da própria pessoa, para a assinatura das propostas. */
    public User changeOwnSignerData(UUID id, String jobTitle, String cpf) {
        User user = get(id);
        user.changeSignerData(jobTitle, cpf);
        return user;
    }

    public void resetPassword(UUID id, String newPassword) {
        User user = get(id);
        requireStrong(newPassword, user.getEmail());
        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAllForUser(id, clock.instant());
    }

    public void changeOwnPassword(UUID id, String currentPassword, String newPassword) {
        User user = get(id);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "A senha atual não confere.");
        }
        requireStrong(newPassword, user.getEmail());
        user.changePassword(passwordEncoder.encode(newPassword));
    }

    private User get(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Usuário não encontrado."));
    }

    private static void requireStrong(String password, String email) {
        PasswordPolicy.problem(password, email).ifPresent(problem -> {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, problem);
        });
    }
}
