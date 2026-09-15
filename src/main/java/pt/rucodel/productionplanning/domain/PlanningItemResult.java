package pt.rucodel.productionplanning.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PlanningItemResult(
        UUID requestId,
        String customerName,
        String driverName,
        int quantity,
        OffsetDateTime availabilityAt,
        OffsetDateTime requiredReadyAt,
        LocalDate assignedProductionDate,
        String assignedWindowLabel,
        AvailabilityClassification availabilityClassification,
        RiskClassification riskClassification,
        BigDecimal priorityScore,
        String priorityExplanation,
        boolean manuallyPrioritised,
        boolean locked,
        AccountingBucket accountingBucket
) {
}
