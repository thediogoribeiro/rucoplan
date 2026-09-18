package pt.rucodel.productionplanning.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record IdentityLinkRequest(
        @NotNull UUID driverId,
        String reason
) {
}
