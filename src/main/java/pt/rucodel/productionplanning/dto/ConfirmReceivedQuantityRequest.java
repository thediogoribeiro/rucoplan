package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConfirmReceivedQuantityRequest(
        @NotNull @Positive Integer actualReceivedWheelQuantity,
        Boolean acknowledgeDiscrepancy,
        String reason,
        @NotNull Long version
) {
}
