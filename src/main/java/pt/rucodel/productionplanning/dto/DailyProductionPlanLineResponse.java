package pt.rucodel.productionplanning.dto;

import pt.rucodel.productionplanning.domain.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DailyProductionPlanLineResponse(
        UUID id,
        UUID requestId,
        String requestCode,
        UUID customerId,
        String customerName,
        String driverName,
        RequestSource source,
        int priorityOrder,
        int requestTotalQuantity,
        int requestRemainingQuantity,
        List<WheelQuantityDto> requestWheelQuantities,
        List<PlanLineWheelQuantityResponse> wheelQuantities,
        int plannedQuantity,
        int completedQuantity,
        int remainingQuantity,
        OffsetDateTime availableAt,
        OffsetDateTime deadlineAt,
        OffsetDateTime factoryDropoffStart,
        OffsetDateTime factoryDropoffEnd,
        OffsetDateTime factoryPickupStart,
        OffsetDateTime factoryPickupEnd,
        String notes,
        String operationalNotes,
        boolean carriedOver,
        boolean advancedFromFuture,
        RiskClassification riskClassification,
        ProductionPlanLineStatus status,
        long version
) {
}
