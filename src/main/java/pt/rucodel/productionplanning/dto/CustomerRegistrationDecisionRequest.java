package pt.rucodel.productionplanning.dto;

import java.util.UUID;

public record CustomerRegistrationDecisionRequest(
        UUID customerId,
        String notes,
        Long version
) {
}
