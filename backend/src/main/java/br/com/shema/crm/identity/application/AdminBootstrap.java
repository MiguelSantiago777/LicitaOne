package br.com.shema.crm.identity.application;

import br.com.shema.crm.identity.domain.Role;
import br.com.shema.crm.identity.domain.User;
import br.com.shema.crm.identity.infrastructure.BootstrapAdminProperties;
import br.com.shema.crm.identity.infrastructure.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria o primeiro administrador a partir de variáveis de ambiente quando ainda não existe nenhum administrador.
 * (O seed de desenvolvimento cria usuários comerciais, por isso o critério é "nenhum ADMIN", não "nenhum usuário".)
 */
@Component
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties properties;

    AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder, BootstrapAdminProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        if (isBlank(properties.email()) || isBlank(properties.password())) {
            log.warn("Nenhum administrador cadastrado e SHEMA_ADMIN_EMAIL/SHEMA_ADMIN_PASSWORD não definidos.");
            return;
        }
        users.save(new User(
                properties.name(), properties.email(), passwordEncoder.encode(properties.password()), Role.ADMIN));
        log.info("Administrador inicial criado");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
