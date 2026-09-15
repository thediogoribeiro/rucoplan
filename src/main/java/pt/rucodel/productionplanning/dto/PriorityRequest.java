package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.NotNull;

public record PriorityRequest(
        Integer manualPriority,
        Boolean planningLocked,
        String reason,
        @NotNull Long version
) {
}
