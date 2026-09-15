package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.NotBlank;

public record DriverRequest(
        String externalId,
        @NotBlank String name,
        Boolean active,
        Long version,
        String username,
        String password
) {
}
