package pt.rucodel.productionplanning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReconciliationRequest(
        @NotNull Long planVersion,
        String notes,
        @Valid List<ReconciliationLineRequest> lines
) {
}
