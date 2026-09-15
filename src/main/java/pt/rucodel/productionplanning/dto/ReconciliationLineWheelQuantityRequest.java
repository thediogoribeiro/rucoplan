package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import pt.rucodel.productionplanning.domain.WheelType;

public record ReconciliationLineWheelQuantityRequest(
        @NotNull WheelType type,
        @NotNull @Min(0) Integer completedQuantity,
        @NotNull @Min(0) Integer remainingQuantity
) {
}
