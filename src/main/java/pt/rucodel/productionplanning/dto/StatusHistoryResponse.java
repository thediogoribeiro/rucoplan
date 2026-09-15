package pt.rucodel.productionplanning.dto;

import pt.rucodel.productionplanning.domain.LifecycleStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StatusHistoryResponse(
        UUID id,
        LifecycleStatus previousStatus,
        LifecycleStatus newStatus,
        OffsetDateTime changedAt,
        UUID actorUserId,
        String actorLabel,
        String reason
) {
}
