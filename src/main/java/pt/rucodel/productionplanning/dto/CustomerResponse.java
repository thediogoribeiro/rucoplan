package pt.rucodel.productionplanning.dto;

import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String externalId,
        String name,
        boolean active,
        long version
) {
}
