package br.com.shema.crm.identity.application;

import br.com.shema.crm.identity.SignerProfile;
import br.com.shema.crm.identity.UserDirectory;
import br.com.shema.crm.identity.UserSummary;
import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.UserRepository;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class UserDirectoryService implements UserDirectory {

    private final UserRepository users;

    UserDirectoryService(UserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<UserSummary> find(UUID id) {
        return id == null ? Optional.empty() : users.findById(id).map(UserDirectoryService::summary);
    }

    @Override
    public Optional<SignerProfile> signer(UUID id) {
        return id == null
                ? Optional.empty()
                : users.findById(id).map(u -> new SignerProfile(u.getName(), u.getJobTitle(), u.getCpf()));
    }

    @Override
    public Map<UUID, UserSummary> findAll(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            // Vazio que aceita get(null): quem chama costuma consultar pelo responsável, que pode faltar
            return Collections.emptyMap();
        }
        return users.findAllById(ids).stream()
                .map(UserDirectoryService::summary)
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }

    @Override
    public Optional<String> activeEmail(UUID id) {
        return id == null
                ? Optional.empty()
                : users.findById(id).filter(User::isActive).map(User::getEmail);
    }

    @Override
    public List<UserSummary> searchActive(String text) {
        String key = plain(text);
        return users.findAll().stream()
                .filter(User::isActive)
                .filter(u -> key.isEmpty() || plain(u.getName()).contains(key))
                .sorted(java.util.Comparator.comparing(User::getName))
                .map(UserDirectoryService::summary)
                .toList();
    }

    private static String plain(String text) {
        return text == null
                ? ""
                : java.text.Normalizer.normalize(text.strip(), java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public List<String> activeAdminEmails() {
        return users.findByRoleAndActiveTrueOrderByName(Role.ADMIN).stream()
                .map(User::getEmail)
                .toList();
    }

    static UserSummary summary(User user) {
        return new UserSummary(user.getId(), user.getName(), user.initials(), user.isActive());
    }
}
