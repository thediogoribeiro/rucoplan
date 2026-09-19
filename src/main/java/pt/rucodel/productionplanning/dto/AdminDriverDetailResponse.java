package pt.rucodel.productionplanning.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminDriverDetailResponse(
        UUID id,
        String driverCode,
        String externalId,
        String name,
        boolean active,
        long version,
        long requestCount,
        OffsetDateTime lastRequestAt,
        List<MessagingIdentityResponse> identities
) {
}
