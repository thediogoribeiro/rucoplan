package pt.rucodel.productionplanning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PlanItemCloseRequest(
        @NotNull Long version,
        @Valid List<PlanItemCloseWheelQuantityRequest> wheelQuantities,
        String operationalNotes
) {
}
