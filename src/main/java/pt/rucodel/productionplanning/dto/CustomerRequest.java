package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomerRequest(
        String externalId,
        @NotBlank String name,
        Boolean active,
        Long version
) {
}
