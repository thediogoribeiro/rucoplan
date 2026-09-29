package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlanItemReopenRequest(
        @NotNull Long version,
        @NotBlank String reason
) {
}
