package pt.rucodel.productionplanning.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        UUID planId,
        UUID requestId,
        String eventType,
        String actor,
        String detail,
        OffsetDateTime createdAt
) {
}
