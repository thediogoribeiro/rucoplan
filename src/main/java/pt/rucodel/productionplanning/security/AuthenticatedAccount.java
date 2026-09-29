package pt.rucodel.productionplanning.security;

import pt.rucodel.productionplanning.domain.UserRole;

import java.util.UUID;

public record AuthenticatedAccount(
        UUID id,
        String username,
        String displayName,
        UserRole role,
        UUID driverId
) {
}
