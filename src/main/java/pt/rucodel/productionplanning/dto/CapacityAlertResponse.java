package pt.rucodel.productionplanning.dto;

import pt.rucodel.productionplanning.domain.CapacityAlertStatus;
import pt.rucodel.productionplanning.domain.CapacityAlertType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CapacityAlertResponse(
        UUID id,
        CapacityAlertType type,
        CapacityAlertStatus status,
        LocalDate affectedDate,
        int requiredQuantity,
        int availableCapacity,
        int deficit,
        List<UUID> affectedRequestIds,
        String message,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime resolvedAt,
        long version
) {
}
