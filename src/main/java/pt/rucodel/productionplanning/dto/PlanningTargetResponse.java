package pt.rucodel.productionplanning.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PlanningTargetResponse(
        UUID id,
        int minimumDailyTarget,
        int regularDailyCapacity,
        LocalDate effectiveFrom,
        String createdBy,
        OffsetDateTime createdAt,
        String source
) {
}
