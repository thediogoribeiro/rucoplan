package pt.rucodel.productionplanning.security.local;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import pt.rucodel.productionplanning.security.AuthenticatedUser;
import pt.rucodel.productionplanning.security.TokenAuthenticationValidator;
import pt.rucodel.productionplanning.security.TokenClaims;

@Service
@Profile("!production")
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "LOCAL", matchIfMissing = true)
public class LocalTokenAuthenticationValidator implements TokenAuthenticationValidator {
    @Override
    public AuthenticatedUser validate(TokenClaims claims) {
        return new AuthenticatedUser(
                claims.userId(),
                claims.username(),
                claims.displayName(),
                claims.role(),
                claims.driverId(),
                claims.productionSiteId(),
                claims.productionSiteCode(),
                claims.productionSiteName(),
                claims.productionSiteTimezone()
        );
    }
}
