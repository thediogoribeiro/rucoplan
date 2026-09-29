package pt.rucodel.productionplanning.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.domain.UserRole;
import pt.rucodel.productionplanning.entity.ApplicationUserEntity;
import pt.rucodel.productionplanning.repository.ApplicationUserRepository;
import pt.rucodel.productionplanning.service.ProductionSiteService;

import java.util.Arrays;

@Configuration
@Profile("production")
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "DATABASE")
public class BootstrapAdminConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdminConfig.class);

    @Bean
    CommandLineRunner bootstrapAdmin(ApplicationUserRepository users,
                                     ProductionSiteService productionSites,
                                     PasswordEncoder passwordEncoder,
                                     @Value("${app.bootstrap-admin.enabled:false}") boolean enabled,
                                     @Value("${app.bootstrap-admin.username:}") String username,
                                     @Value("${app.bootstrap-admin.password:}") String password,
                                     @Value("${app.bootstrap-admin.display-name:Administrador}") String displayName,
                                     @Value("${app.bootstrap-admin.sites:PT,LUX}") String sites) {
        return args -> {
            if (!enabled) {
                return;
            }
            boolean credentialsPresent = username != null && !username.isBlank()
                    && password != null && !password.isBlank();
            if (!credentialsPresent) {
                LOGGER.warn("Bootstrap admin is enabled but username/password are missing; no user was created.");
                return;
            }
            if (users.count() > 0) {
                LOGGER.info("Production bootstrap administrator skipped because user table is not empty.");
                return;
            }
            String normalizedUsername = username.trim();
            ApplicationUserEntity user = new ApplicationUserEntity();
            user.setUsername(normalizedUsername);
            user.setDisplayName(displayName == null || displayName.isBlank() ? "Administrador" : displayName.trim());
            user.setRole(UserRole.ADMIN);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setActive(true);
            user.setCreatedBy("BOOTSTRAP");
            user.setUpdatedBy("BOOTSTRAP");
            ApplicationUserEntity saved = users.save(user);
            Arrays.stream(sites.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(ProductionSiteCode::parse)
                    .filter(java.util.Objects::nonNull)
                    .map(productionSites::requireActive)
                    .forEach(site -> productionSites.ensureUserAssociation(saved, site, "BOOTSTRAP"));
            LOGGER.info("Production bootstrap administrator created.");
        };
    }
}
