package pt.rucodel.productionplanning.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import pt.rucodel.productionplanning.domain.UserRole;
import pt.rucodel.productionplanning.entity.ApplicationUserEntity;
import pt.rucodel.productionplanning.repository.ApplicationUserRepository;

@Configuration
public class BootstrapAdminConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdminConfig.class);

    @Bean
    CommandLineRunner bootstrapAdmin(ApplicationUserRepository users,
                                     PasswordEncoder passwordEncoder,
                                     @Value("${app.bootstrap-admin.enabled:false}") boolean enabled,
                                     @Value("${app.bootstrap-admin.username:}") String username,
                                     @Value("${app.bootstrap-admin.password:}") String password,
                                     @Value("${app.bootstrap-admin.display-name:Administrador}") String displayName) {
        return args -> {
            if (!enabled) {
                return;
            }
            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                LOGGER.warn("Bootstrap admin is enabled but username/password are missing; no user was created.");
                return;
            }
            String normalizedUsername = username.trim();
            ApplicationUserEntity existing = users.findByUsername(normalizedUsername).orElse(null);
            if (existing != null) {
                boolean changed = false;
                String normalizedDisplayName = displayName == null || displayName.isBlank() ? "Administrador" : displayName.trim();
                if (!passwordEncoder.matches(password, existing.getPasswordHash())) {
                    existing.setPasswordHash(passwordEncoder.encode(password));
                    changed = true;
                }
                if (existing.getRole() != UserRole.ADMIN) {
                    existing.setRole(UserRole.ADMIN);
                    changed = true;
                }
                if (!existing.isActive()) {
                    existing.setActive(true);
                    changed = true;
                }
                if (!normalizedDisplayName.equals(existing.getDisplayName())) {
                    existing.setDisplayName(normalizedDisplayName);
                    changed = true;
                }
                if (changed) {
                    existing.setUpdatedBy("BOOTSTRAP");
                    users.save(existing);
                    LOGGER.info("Development bootstrap administrator refreshed.");
                }
                return;
            }
            ApplicationUserEntity user = new ApplicationUserEntity();
            user.setUsername(normalizedUsername);
            user.setDisplayName(displayName == null || displayName.isBlank() ? "Administrador" : displayName.trim());
            user.setRole(UserRole.ADMIN);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setActive(true);
            user.setCreatedBy("BOOTSTRAP");
            user.setUpdatedBy("BOOTSTRAP");
            users.save(user);
            LOGGER.info("Development bootstrap administrator created.");
        };
    }
}
