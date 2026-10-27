package br.com.shema.crm.identity.application;

import br.com.shema.crm.identity.UserSummary;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.UserRepository;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserQueries {

    private final UserRepository users;

    public UserQueries(UserRepository users) {
        this.users = users;
    }

    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Usuário não encontrado."));
    }

    public List<User> list() {
        return users.findAll(Sort.by("name"));
    }

    public List<UserSummary> activeSummaries() {
        return users.findAll(Sort.by("name")).stream()
                .filter(User::isActive)
                .map(UserDirectoryService::summary)
                .toList();
    }
}
