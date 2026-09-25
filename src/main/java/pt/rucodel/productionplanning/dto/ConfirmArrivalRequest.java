package pt.rucodel.productionplanning.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

public record ConfirmArrivalRequest(
        @JsonAlias("actualArrivalAt")
        OffsetDateTime actualFactoryArrivalAt,
        @Positive Integer actualReceivedWheelQuantity,
        Boolean acknowledgeDiscrepancy,
        String reason,
        Long version
) {
}
