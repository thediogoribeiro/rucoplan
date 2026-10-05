package pt.rucodel.productionplanning.security.production;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.dto.LoginRequest;
import pt.rucodel.productionplanning.entity.ApplicationUserEntity;
import pt.rucodel.productionplanning.entity.ProductionSiteEntity;
import pt.rucodel.productionplanning.repository.ApplicationUserRepository;
import pt.rucodel.productionplanning.security.AuthenticatedAccount;
import pt.rucodel.productionplanning.security.AuthenticationProvider;
import pt.rucodel.productionplanning.security.UsernameNormalizer;
import pt.rucodel.productionplanning.service.ProductionSiteService;

import java.util.Optional;

@Service
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "DATABASE")
public class DatabaseAuthenticationProvider implements AuthenticationProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseAuthenticationProvider.class);

    private final ApplicationUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ProductionSiteService productionSites;

    public DatabaseAuthenticationProvider(ApplicationUserRepository users, PasswordEncoder passwordEncoder,
                                          ProductionSiteService productionSites) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.productionSites = productionSites;
    }

    @Override
    public AuthenticatedAccount authenticate(LoginRequest request) {
        String normalizedUsername = UsernameNormalizer.normalize(request.username());
        LOGGER.info("auth.login.lookup correlationId={} usernameNormalized={} encoder={} result=START",
                correlationId(), normalizedUsername, passwordEncoder.getClass().getSimpleName());

        Optional<ApplicationUserEntity> found = users.findWithDriverByNormalizedUsername(normalizedUsername);
        if (found.isEmpty()) {
            LOGGER.info("auth.login.result correlationId={} usernameNormalized={} userFound=false failureReason=USER_NOT_FOUND outcome=FAILURE",
                    correlationId(), normalizedUsername);
            throw new BadCredentialsException("Invalid credentials");
        }

        ApplicationUserEntity user = found.get();
        if (!user.isActive()) {
            LOGGER.info("auth.login.result correlationId={} usernameNormalized={} userFound=true active=false failureReason=USER_INACTIVE outcome=FAILURE",
                    correlationId(), normalizedUsername);
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            LOGGER.info("auth.login.result correlationId={} usernameNormalized={} userFound=true active=true failureReason=PASSWORD_MISMATCH outcome=FAILURE",
                    correlationId(), normalizedUsername);
            throw new BadCredentialsException("Invalid credentials");
        }
        LOGGER.info("auth.login.result correlationId={} usernameNormalized={} userFound=true active=true failureReason=NONE outcome=SUCCESS",
                correlationId(), normalizedUsername);
        return new AuthenticatedAccount(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.getDriver() == null ? null : user.getDriver().getId()
        );
    }

    @Override
    public ProductionSiteEntity requireSite(AuthenticatedAccount account, ProductionSiteCode siteCode) {
        ApplicationUserEntity user = users.findById(account.id())
                .filter(ApplicationUserEntity::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        return productionSites.requireUserSite(user, siteCode);
    }

    private String correlationId() {
        String value = MDC.get("correlationId");
        return value == null || value.isBlank() ? "unavailable" : value;
    }
}
