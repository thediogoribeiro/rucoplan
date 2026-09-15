package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record TimeWindowDto(
        @NotBlank String label,
        @NotNull LocalTime cutoffTime,
        Integer sortOrder
) {
}
