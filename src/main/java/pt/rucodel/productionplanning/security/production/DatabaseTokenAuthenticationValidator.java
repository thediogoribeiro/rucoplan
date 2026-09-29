package pt.rucodel.productionplanning.security.production;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pt.rucodel.productionplanning.entity.ApplicationUserEntity;
import pt.rucodel.productionplanning.exception.InvalidRequestException;
import pt.rucodel.productionplanning.repository.ApplicationUserRepository;
import pt.rucodel.productionplanning.security.AuthenticatedUser;
import pt.rucodel.productionplanning.security.TokenAuthenticationValidator;
import pt.rucodel.productionplanning.security.TokenClaims;

@Service
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "DATABASE")
public class DatabaseTokenAuthenticationValidator implements TokenAuthenticationValidator {
    private final ApplicationUserRepository users;

    public DatabaseTokenAuthenticationValidator(ApplicationUserRepository users) {
        this.users = users;
    }

    @Override
    public AuthenticatedUser validate(TokenClaims claims) {
        ApplicationUserEntity user = users.findWithDriverByUsername(claims.username())
                .filter(ApplicationUserEntity::isActive)
                .filter(entity -> entity.getId().equals(claims.userId()))
                .orElseThrow(() -> new InvalidRequestException("AUTHENTICATION_FAILED", "Invalid authentication token."));
        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.getDriver() == null ? null : user.getDriver().getId(),
                claims.productionSiteId(),
                claims.productionSiteCode(),
                claims.productionSiteName(),
                claims.productionSiteTimezone()
        );
    }
}
