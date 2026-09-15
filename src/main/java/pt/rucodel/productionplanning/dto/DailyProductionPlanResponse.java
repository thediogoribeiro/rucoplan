package pt.rucodel.productionplanning.dto;

import pt.rucodel.productionplanning.domain.GenerationTrigger;
import pt.rucodel.productionplanning.domain.ProductionPlanStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DailyProductionPlanResponse(
        UUID id,
        LocalDate planningDate,
        int versionNumber,
        ProductionPlanStatus status,
        int minimumDailyTarget,
        int regularDailyCapacity,
        List<WheelQuantityDto> wheelQuantities,
        int totalPlanned,
        int totalCompleted,
        int totalRemaining,
        int differenceToMinimum,
        int overtimeQuantity,
        int carriedOverQuantity,
        int advancedQuantity,
        int atRiskQuantity,
        boolean overtimeRequired,
        String warning,
        OffsetDateTime generatedAt,
        OffsetDateTime closedAt,
        String closedBy,
        GenerationTrigger generationTrigger,
        long version,
        List<DailyProductionPlanLineResponse> lines
) {
}
