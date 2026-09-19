package pt.rucodel.productionplanning.dto;

import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String customerCode,
        Integer customerNumber,
        String externalId,
        String externalSystem,
        String externalCustomerId,
        String name,
        String taxIdentifier,
        String countryCode,
        String locality,
        String status,
        boolean active,
        long version
) {
}
