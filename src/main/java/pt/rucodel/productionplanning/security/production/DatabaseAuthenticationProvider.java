package pt.rucodel.productionplanning.security.production;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
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
    private final AuthenticationManager authenticationManager;

    public DatabaseAuthenticationProvider(ApplicationUserRepository users, PasswordEncoder passwordEncoder,
                                          ProductionSiteService productionSites,
                                          @Qualifier("databaseAuthenticationManager") AuthenticationManager authenticationManager) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.productionSites = productionSites;
        this.authenticationManager = authenticationManager;
    }

    @Override
    public AuthenticatedAccount authenticate(LoginRequest request) {
        String normalizedUsername = UsernameNormalizer.normalize(request.username());
        LOGGER.info("auth.login.repository.lookup correlationId={} usernameNormalized={} repositoryMethod=findWithDriverByNormalizedUsername result=START",
                correlationId(), normalizedUsername);

        Optional<ApplicationUserEntity> found;
        try {
            found = users.findWithDriverByNormalizedUsername(normalizedUsername);
        } catch (DataAccessException ex) {
            LOGGER.info("auth.login.repository.lookup correlationId={} usernameNormalized={} failureReason=DATABASE_ERROR outcome=FAILURE",
                    correlationId(), normalizedUsername);
            throw new BadCredentialsException("Invalid credentials", ex);
        }

        if (found.isEmpty()) {
            LOGGER.info("auth.login.repository.lookup correlationId={} usernameNormalized={} userFound=false failureReason=USER_NOT_FOUND outcome=FAILURE",
                    correlationId(), normalizedUsername);
            throw new BadCredentialsException("Invalid credentials");
        }
        LOGGER.info("auth.login.repository.lookup correlationId={} usernameNormalized={} userFound=true outcome=SUCCESS",
                correlationId(), normalizedUsername);

        ApplicationUserEntity user = found.get();
        LOGGER.info("auth.login.account.state correlationId={} usernameNormalized={} active={} locked=NOT_MAPPED status=NOT_MAPPED lockedUntil=NOT_MAPPED",
                correlationId(), normalizedUsername, user.isActive());
        if (!user.isActive()) {
            LOGGER.info("auth.login.result correlationId={} usernameNormalized={} userFound=true active=false failureReason=ACCOUNT_DISABLED outcome=FAILURE",
                    correlationId(), normalizedUsername);
            throw new BadCredentialsException("Invalid credentials");
        }

        LOGGER.info("auth.login.passwordEncoder correlationId={} usernameNormalized={} encoder={} prefixHandling=NONE result=START",
                correlationId(), normalizedUsername, passwordEncoder.getClass().getSimpleName());
        boolean passwordMatches;
        try {
            passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());
        } catch (RuntimeException ex) {
            LOGGER.info("auth.login.passwordEncoder correlationId={} usernameNormalized={} encoder={} failureReason=PASSWORD_ENCODER_ERROR outcome=FAILURE",
                    correlationId(), normalizedUsername, passwordEncoder.getClass().getSimpleName());
            throw new BadCredentialsException("Invalid credentials", ex);
        }
        LOGGER.info("auth.login.passwordEncoder correlationId={} usernameNormalized={} matches={} outcome={}",
                correlationId(), normalizedUsername, passwordMatches, passwordMatches ? "SUCCESS" : "FAILURE");
        if (!passwordMatches) {
            LOGGER.info("auth.login.result correlationId={} usernameNormalized={} userFound=true active=true failureReason=PASSWORD_MISMATCH outcome=FAILURE",
                    correlationId(), normalizedUsername);
            throw new BadCredentialsException("Invalid credentials");
        }
        LOGGER.info("auth.login.authenticationManager correlationId={} usernameNormalized={} result=START",
                correlationId(), normalizedUsername);
        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalizedUsername, request.password()));
            LOGGER.info("auth.login.authenticationManager correlationId={} usernameNormalized={} result=SUCCESS",
                    correlationId(), normalizedUsername);
        } catch (AuthenticationException ex) {
            LOGGER.info("auth.login.authenticationManager correlationId={} usernameNormalized={} failureReason=AUTHENTICATION_PROVIDER_ERROR outcome=FAILURE",
                    correlationId(), normalizedUsername);
            throw new BadCredentialsException("Invalid credentials", ex);
        }
        LOGGER.info("auth.login.authenticatedAccount.create correlationId={} usernameNormalized={} result=START",
                correlationId(), normalizedUsername);
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

    @Override
    public boolean usesAuthenticationManager() {
        return true;
    }

    @Override
    public String userSource() {
        return "DATABASE";
    }
}
