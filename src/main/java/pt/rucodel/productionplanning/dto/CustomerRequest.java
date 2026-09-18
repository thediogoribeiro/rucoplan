package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomerRequest(
        String externalId,
        String externalSystem,
        String externalCustomerId,
        @NotBlank String name,
        String taxIdentifier,
        String countryCode,
        String locality,
        Boolean active,
        Long version
) {
}
