package pt.rucodel.productionplanning.dto;

import pt.rucodel.productionplanning.domain.LifecycleStatus;
import pt.rucodel.productionplanning.domain.RequestSource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RequestResponse(
        UUID id,
        String requestCode,
        RequestSource source,
        String externalSourceReference,
        String externalMessageId,
        UUID customerId,
        String customerExternalId,
        String customerNameSnapshot,
        UUID driverId,
        String driverName,
        UUID submittedByIdentityId,
        List<WheelQuantityDto> wheelQuantities,
        int totalQuantity,
        @Deprecated
        int expectedWheelQuantity,
        Integer actualReceivedWheelQuantity,
        boolean quantityDiscrepancy,
        boolean quantityDiscrepancyAcknowledged,
        OffsetDateTime expectedFactoryDropOffWindowStart,
        OffsetDateTime expectedFactoryDropOffWindowEnd,
        OffsetDateTime requestedFactoryPickupWindowStart,
        OffsetDateTime requestedFactoryPickupWindowEnd,
        OffsetDateTime actualFactoryArrivalAt,
        OffsetDateTime arrivalConfirmedAt,
        String arrivalConfirmedBy,
        String arrivalConfirmationSource,
        OffsetDateTime actualPickupFromFactoryAt,
        String rucopiProductionJobId,
        String notes,
        LifecycleStatus lifecycleStatus,
        Integer manualPriority,
        boolean planningLocked,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version
) {
}
