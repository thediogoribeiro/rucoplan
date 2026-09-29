package pt.rucodel.productionplanning.dto;

import pt.rucodel.productionplanning.domain.ProductionSiteCode;

import java.util.UUID;

public record ProductionSiteResponse(
        UUID id,
        ProductionSiteCode code,
        String displayName,
        String timezone,
        boolean active
) {
}
