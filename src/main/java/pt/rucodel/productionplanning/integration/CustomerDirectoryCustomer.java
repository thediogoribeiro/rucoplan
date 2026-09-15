package pt.rucodel.productionplanning.integration;

import java.util.UUID;

public record CustomerDirectoryCustomer(
        UUID localId,
        String externalId,
        String officialName
) {
}
