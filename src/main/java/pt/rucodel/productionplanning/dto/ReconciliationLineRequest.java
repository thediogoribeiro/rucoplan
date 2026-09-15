package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReconciliationLineRequest(
        @NotNull UUID lineId,
        @Min(0) Integer completedQuantity,
        @Min(0) Integer remainingQuantity,
        List<ReconciliationLineWheelQuantityRequest> wheelQuantities,
        String operationalNotes,
        @NotNull Long version
) {
}
