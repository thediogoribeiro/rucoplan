package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

public record WhatsAppIntakeRequest(
        @NotBlank String externalMessageId,
        @NotBlank String driverExternalId,
        String customerExternalId,
        @NotBlank String customerName,
        @Deprecated Integer wheelQuantity,
        List<WheelQuantityDto> wheelQuantities,
        @NotNull OffsetDateTime expectedFactoryDropOffWindowStart,
        @NotNull OffsetDateTime expectedFactoryDropOffWindowEnd,
        @NotNull OffsetDateTime requestedFactoryPickupWindowStart,
        @NotNull OffsetDateTime requestedFactoryPickupWindowEnd,
        String notes
) {
}
