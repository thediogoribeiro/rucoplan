package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RequestCreateRequest(
        @NotNull UUID customerId,
        @Deprecated Integer expectedWheelQuantity,
        List<WheelQuantityDto> wheelQuantities,
        @NotNull OffsetDateTime expectedFactoryDropOffWindowStart,
        @NotNull OffsetDateTime expectedFactoryDropOffWindowEnd,
        @NotNull OffsetDateTime requestedFactoryPickupWindowStart,
        @NotNull OffsetDateTime requestedFactoryPickupWindowEnd,
        String notes
) {
}
