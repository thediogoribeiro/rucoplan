package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

public record ConfirmArrivalRequest(
        @NotNull OffsetDateTime actualFactoryArrivalAt,
        @Positive Integer actualReceivedWheelQuantity,
        Boolean acknowledgeDiscrepancy,
        String reason,
        @NotNull Long version
) {
}
