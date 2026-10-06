package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomerRequest(
        String externalId,
        String externalSystem,
        String externalCustomerId,
        @NotBlank(message = "O nome do cliente é obrigatório.") String name,
        String taxIdentifier,
        String countryCode,
        String countryName,
        String locality,
        Boolean active,
        Long version
) {
}
