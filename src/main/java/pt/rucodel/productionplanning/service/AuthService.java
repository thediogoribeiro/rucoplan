package pt.rucodel.productionplanning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.security.AuthenticatedAccount;
import pt.rucodel.productionplanning.dto.AuthUserResponse;
import pt.rucodel.productionplanning.dto.LoginRequest;
import pt.rucodel.productionplanning.dto.LoginResponse;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.entity.ProductionSiteEntity;
import pt.rucodel.productionplanning.security.AuthenticatedUser;
import pt.rucodel.productionplanning.security.AuthenticationProvider;
import pt.rucodel.productionplanning.security.TokenService;
import pt.rucodel.productionplanning.security.UsernameNormalizer;

@Service
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationProvider authenticationProvider;
    private final TokenService tokenService;
    private final ProductionSiteService productionSites;

    public AuthService(AuthenticationProvider authenticationProvider, TokenService tokenService,
                       ProductionSiteService productionSites) {
        this.authenticationProvider = authenticationProvider;
        this.tokenService = tokenService;
        this.productionSites = productionSites;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String normalizedUsername = UsernameNormalizer.normalize(request.username());
        LOGGER.info("auth.login.service.enter correlationId={} usernameNormalized={} authenticationProvider={} authenticationManager={} userSource={} session=STATELESS_TOKEN",
                correlationId(), normalizedUsername, authenticationProvider.getClass().getSimpleName(),
                authenticationProvider.usesAuthenticationManager() ? "USED" : "NOT_USED_CUSTOM_PROVIDER",
                authenticationProvider.userSource());
        AuthenticatedAccount account = authenticationProvider.authenticate(request);
        LOGGER.info("auth.login.authenticatedAccount.created correlationId={} usernameNormalized={} result=SUCCESS",
                correlationId(), normalizedUsername);
        ProductionSiteCode siteCode = ProductionSiteCode.parse(request.productionSite());
        if (siteCode == null) {
            LOGGER.info("auth.login.final correlationId={} usernameNormalized={} failureReason=INVALID_PRODUCTION_SITE outcome=FAILURE",
                    correlationId(), normalizedUsername);
            throw new BadCredentialsException("Invalid credentials");
        }
        ProductionSiteEntity site = authenticationProvider.requireSite(account, siteCode);
        LOGGER.info("auth.login.session.create correlationId={} usernameNormalized={} sessionType=HTTP_SESSION status=NOT_CREATED_STATELESS_TOKEN_AUTH",
                correlationId(), normalizedUsername);
        LOGGER.info("auth.login.token.create correlationId={} usernameNormalized={} result=START",
                correlationId(), normalizedUsername);
        TokenService.IssuedToken issued = tokenService.issue(account, site);
        LOGGER.info("auth.login.token.create correlationId={} usernameNormalized={} result=SUCCESS",
                correlationId(), normalizedUsername);
        LOGGER.info("auth.login.final correlationId={} usernameNormalized={} productionSite={} failureReason=NONE outcome=SUCCESS",
                correlationId(), normalizedUsername, site.getCode());
        return new LoginResponse(issued.token(), issued.expiresAt(), toResponse(account, site));
    }

    public AuthUserResponse me(AuthenticatedUser user) {
        ProductionSiteEntity site = productionSites.requireById(user.productionSiteId());
        return new AuthUserResponse(user.id(), user.username(), user.displayName(), user.role(), user.driverId(),
                productionSites.toResponse(site));
    }

    private AuthUserResponse toResponse(AuthenticatedAccount user, ProductionSiteEntity site) {
        return new AuthUserResponse(
                user.id(),
                user.username(),
                user.displayName(),
                user.role(),
                user.driverId(),
                productionSites.toResponse(site)
        );
    }

    private String correlationId() {
        String value = MDC.get("correlationId");
        return value == null || value.isBlank() ? "unavailable" : value;
    }
}
