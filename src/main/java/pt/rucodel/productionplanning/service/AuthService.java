package pt.rucodel.productionplanning.service;

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

@Service
public class AuthService {
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
        AuthenticatedAccount account = authenticationProvider.authenticate(request);
        ProductionSiteCode siteCode = ProductionSiteCode.parse(request.productionSite());
        if (siteCode == null) {
            throw new BadCredentialsException("Invalid credentials");
        }
        ProductionSiteEntity site = authenticationProvider.requireSite(account, siteCode);
        TokenService.IssuedToken issued = tokenService.issue(account, site);
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
}
