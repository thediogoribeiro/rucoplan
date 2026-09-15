package pt.rucodel.productionplanning.dto;

import pt.rucodel.productionplanning.domain.UserRole;

import java.util.UUID;

public record AuthUserResponse(
        UUID id,
        String username,
        String displayName,
        UserRole role,
        UUID driverId
) {
}
