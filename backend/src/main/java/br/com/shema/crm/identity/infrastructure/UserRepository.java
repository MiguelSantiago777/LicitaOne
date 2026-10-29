package br.com.shema.crm.identity.infrastructure;

import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByRole(Role role);

    List<User> findByRoleAndActiveTrueOrderByName(Role role);
}
