package pt.rucodel.productionplanning.security;

import pt.rucodel.productionplanning.domain.UserRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TokenClaims(
        UUID userId,
        String username,
        String displayName,
        UserRole role,
        UUID driverId,
        OffsetDateTime expiresAt
) {
}
