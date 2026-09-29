package pt.rucodel.productionplanning.security.production;

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
import pt.rucodel.productionplanning.service.ProductionSiteService;

@Service
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "DATABASE")
public class DatabaseAuthenticationProvider implements AuthenticationProvider {
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
        ApplicationUserEntity user = users.findWithDriverByUsername(request.username())
                .filter(ApplicationUserEntity::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
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
}
