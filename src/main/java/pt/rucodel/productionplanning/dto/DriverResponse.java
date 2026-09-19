package pt.rucodel.productionplanning.dto;

import java.util.UUID;

public record DriverResponse(
        UUID id,
        String driverCode,
        String externalId,
        String name,
        boolean active,
        long version
) {
}
