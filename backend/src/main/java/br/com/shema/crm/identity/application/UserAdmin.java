package br.com.shema.crm.identity.application;

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

    public static final int MIN_PASSWORD_LENGTH = 10;

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
        requireStrong(password);
        if (users.findByEmail(User.normalizeEmail(email)).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "Já existe um usuário com este e-mail.");
        }
        return users.save(new User(name, email, passwordEncoder.encode(password), role));
    }

    public User update(UUID id, UUID actingUserId, String name, Role role, boolean active) {
        User user = get(id);
        if (id.equals(actingUserId) && (!active || role != Role.ADMIN)) {
            throw new ApiException(ErrorCode.CONFLICT, "Você não pode tirar o próprio acesso de administrador.");
        }
        user.changeProfile(name, role, active);
        if (!active) {
            refreshTokens.revokeAllForUser(id, clock.instant());
        }
        return user;
    }

    public void resetPassword(UUID id, String newPassword) {
        requireStrong(newPassword);
        User user = get(id);
        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAllForUser(id, clock.instant());
    }

    public void changeOwnPassword(UUID id, String currentPassword, String newPassword) {
        User user = get(id);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "A senha atual não confere.");
        }
        requireStrong(newPassword);
        user.changePassword(passwordEncoder.encode(newPassword));
    }

    private User get(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Usuário não encontrado."));
    }

    private static void requireStrong(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "A senha precisa ter pelo menos " + MIN_PASSWORD_LENGTH + " caracteres.");
        }
    }
}
