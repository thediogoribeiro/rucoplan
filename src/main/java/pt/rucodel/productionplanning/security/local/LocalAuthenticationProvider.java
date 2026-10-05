package pt.rucodel.productionplanning.security.local;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.domain.UserRole;
import pt.rucodel.productionplanning.dto.LoginRequest;
import pt.rucodel.productionplanning.entity.ProductionSiteEntity;
import pt.rucodel.productionplanning.security.AuthenticatedAccount;
import pt.rucodel.productionplanning.security.AuthenticationProvider;
import pt.rucodel.productionplanning.service.ProductionSiteService;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@Profile("!production")
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "LOCAL", matchIfMissing = true)
public class LocalAuthenticationProvider implements AuthenticationProvider {
    private final LocalAuthenticationProperties properties;
    private final ProductionSiteService productionSites;

    public LocalAuthenticationProvider(LocalAuthenticationProperties properties, ProductionSiteService productionSites) {
        this.properties = properties;
        this.productionSites = productionSites;
    }

    @Override
    public AuthenticatedAccount authenticate(LoginRequest request) {
        if (!properties.username().equals(request.username()) || !properties.password().equals(request.password())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return new AuthenticatedAccount(
                UUID.nameUUIDFromBytes(("local-admin:" + properties.username()).getBytes(StandardCharsets.UTF_8)),
                properties.username(),
                properties.displayName(),
                UserRole.ADMIN,
                null
        );
    }

    @Override
    public ProductionSiteEntity requireSite(AuthenticatedAccount account, ProductionSiteCode siteCode) {
        return productionSites.requireActive(siteCode);
    }

    @Override
    public String userSource() {
        return "LOCAL";
    }
}
