package pt.rucodel.productionplanning.dto;

import pt.rucodel.productionplanning.domain.WheelType;

public record PlanLineWheelQuantityResponse(
        WheelType type,
        String label,
        int plannedQuantity,
        int completedQuantity,
        int remainingQuantity
) {
}
