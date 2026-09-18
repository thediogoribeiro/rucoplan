package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.NotBlank;

public record DriverNameUpdateRequest(
        @NotBlank String name,
        Long version
) {
}
