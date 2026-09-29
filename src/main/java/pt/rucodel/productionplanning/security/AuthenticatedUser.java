package pt.rucodel.productionplanning.security;

import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.domain.UserRole;

import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String username,
        String displayName,
        UserRole role,
        UUID driverId,
        UUID productionSiteId,
        ProductionSiteCode productionSiteCode,
        String productionSiteName,
        String productionSiteTimezone
) {
    public AuthenticatedUser(UUID id, String username, String displayName, UserRole role, UUID driverId) {
        this(id, username, displayName, role, driverId, null,
                ProductionSiteCode.PT, ProductionSiteCode.PT.displayName(), ProductionSiteCode.PT.timezone());
    }

    public String actorLabel() {
        return displayName + " (" + username + ")";
    }
}
