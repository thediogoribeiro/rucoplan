package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record PlanningTargetRequest(
        @NotNull @Min(0) Integer minimumDailyTarget,
        @NotNull @Positive Integer regularDailyCapacity,
        @NotNull LocalDate effectiveFrom
) {
}
