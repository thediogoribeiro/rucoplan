package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.NotNull;
import pt.rucodel.productionplanning.domain.LifecycleStatus;

import java.time.OffsetDateTime;

public record UpdateStatusRequest(
        @NotNull LifecycleStatus status,
        OffsetDateTime actualPickupFromFactoryAt,
        String reason,
        @NotNull Long version
) {
}
