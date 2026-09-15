package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReopenPlanRequest(
        @NotNull Long planVersion,
        @NotBlank String reason
) {
}
