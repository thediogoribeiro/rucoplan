package pt.rucodel.productionplanning.dto;

import pt.rucodel.productionplanning.domain.MessagingChannel;
import pt.rucodel.productionplanning.domain.MessagingIdentityOnboardingStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MessagingIdentityResponse(
        UUID id,
        UUID driverId,
        String driverName,
        MessagingChannel channel,
        String integrationKey,
        String externalUserId,
        String externalChatId,
        String externalUsername,
        String platformFirstName,
        String platformLastName,
        String languageCode,
        String maskedPhoneNumber,
        OffsetDateTime firstSeenAt,
        OffsetDateTime lastSeenAt,
        MessagingIdentityOnboardingStatus onboardingStatus,
        OffsetDateTime onboardingCompletedAt,
        OffsetDateTime blockedAt,
        long requestCount,
        OffsetDateTime lastRequestAt,
        long version
) {
}
