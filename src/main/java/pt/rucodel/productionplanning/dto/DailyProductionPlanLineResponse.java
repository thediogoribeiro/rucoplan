package pt.rucodel.productionplanning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
        AvailabilityClassification availabilityClassification,
        LifecycleStatus requestLifecycleStatus,
        OffsetDateTime factoryDropoffStart,
        OffsetDateTime factoryDropoffEnd,
        OffsetDateTime factoryPickupStart,
        OffsetDateTime factoryPickupEnd,
        String notes,
        String operationalNotes,
        boolean carriedOver,
        boolean advancedFromFuture,
        String priorityExplanation,
        RiskClassification riskClassification,
        ProductionPlanLineStatus status,
        long version
) {
    @JsonProperty("totalQuantity")
    public int totalQuantity() {
        return plannedQuantity;
    }

    @JsonProperty("bipartiteQuantity")
    public int bipartiteQuantity() {
        return plannedQuantity(WheelType.BIPARTITE);
    }

    @JsonProperty("washedQuantity")
    public int washedQuantity() {
        return plannedQuantity(WheelType.WASHED);
    }

    @JsonProperty("normalQuantity")
    public int normalQuantity() {
        return plannedQuantity(WheelType.NORMAL);
    }

    private int plannedQuantity(WheelType type) {
        if (wheelQuantities == null) {
            return 0;
        }
        return wheelQuantities.stream()
                .filter(quantity -> quantity.type() == type)
                .mapToInt(PlanLineWheelQuantityResponse::plannedQuantity)
                .findFirst()
                .orElse(0);
    }
}
