package pt.rucodel.productionplanning.dto;

import java.time.OffsetDateTime;

public record LoginResponse(
        String token,
        OffsetDateTime expiresAt,
        AuthUserResponse user
) {
}
