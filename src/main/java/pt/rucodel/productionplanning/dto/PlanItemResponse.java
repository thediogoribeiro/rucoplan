package pt.rucodel.productionplanning.dto;

import pt.rucodel.productionplanning.domain.AvailabilityClassification;
import pt.rucodel.productionplanning.domain.RiskClassification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PlanItemResponse(
        UUID id,
        UUID requestId,
        String customerName,
        String driverName,
        List<PlanLineWheelQuantityResponse> wheelQuantities,
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
        boolean locked
) {
}
