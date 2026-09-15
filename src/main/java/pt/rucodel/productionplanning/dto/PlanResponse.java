package pt.rucodel.productionplanning.dto;

import pt.rucodel.productionplanning.domain.GenerationTrigger;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        LocalDate planningDate,
        int versionNumber,
        OffsetDateTime generatedAt,
        GenerationTrigger generationTrigger,
        int capacityUsed,
        int targetUsed,
        int totalKnownWheels,
        int totalPlanned,
        int totalWaitingForArrival,
        int totalFutureWorkload,
        int totalAtRisk,
        int totalOverCapacity,
        boolean fallbackEstimatesUsed,
        boolean requiresRecalculation,
        String warning,
        List<PlanItemResponse> items
) {
}
