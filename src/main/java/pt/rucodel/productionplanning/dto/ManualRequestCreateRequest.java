package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.NotNull;
import pt.rucodel.productionplanning.domain.FactoryTimeSlot;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ManualRequestCreateRequest(
        @NotNull UUID customerReferenceId,
        UUID driverId,
        List<WheelQuantityDto> wheelQuantities,
        @NotNull Boolean alreadyAtFactory,
        OffsetDateTime actualArrivalAt,
        LocalDate expectedFactoryDropoffDate,
        FactoryTimeSlot expectedFactoryDropoffWindow,
        @NotNull LocalDate requestedPickupDate,
        @NotNull FactoryTimeSlot requestedPickupWindow,
        String notes
) {
}
