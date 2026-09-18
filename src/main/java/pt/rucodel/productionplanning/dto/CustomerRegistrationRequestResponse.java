package pt.rucodel.productionplanning.dto;

import pt.rucodel.productionplanning.domain.CustomerRegistrationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerRegistrationRequestResponse(
        UUID id,
        String proposedName,
        String customerNumber,
        String maskedTaxIdentifier,
        String countryCode,
        String locality,
        UUID requestedByDriverId,
        String requestedByDriverName,
        UUID requestedByIdentityId,
        CustomerRegistrationStatus status,
        UUID matchedCustomerId,
        String matchedCustomerName,
        OffsetDateTime createdAt,
        OffsetDateTime reviewedAt,
        String reviewedBy,
        String reviewNotes,
        long version
) {
}
