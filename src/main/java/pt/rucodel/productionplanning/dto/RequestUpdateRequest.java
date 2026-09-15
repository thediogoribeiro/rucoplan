package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RequestUpdateRequest(
        UUID customerId,
        @Deprecated Integer expectedWheelQuantity,
        List<WheelQuantityDto> wheelQuantities,
        OffsetDateTime expectedFactoryDropOffWindowStart,
        OffsetDateTime expectedFactoryDropOffWindowEnd,
        OffsetDateTime requestedFactoryPickupWindowStart,
        OffsetDateTime requestedFactoryPickupWindowEnd,
        String notes,
        Integer manualPriority,
        Boolean planningLocked,
        @NotNull Long version
) {
}
