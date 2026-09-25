package pt.rucodel.productionplanning.domain;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PlanningWorkItem(
        UUID requestId,
        String customerName,
        String driverName,
        int quantity,
        OffsetDateTime registeredAt,
        OffsetDateTime expectedFactoryDropOffWindowStart,
        OffsetDateTime expectedFactoryDropOffWindowEnd,
        OffsetDateTime actualFactoryArrivalAt,
        OffsetDateTime requestedFactoryPickupWindowStart,
        LifecycleStatus lifecycleStatus,
        Integer manualPriority,
        boolean planningLocked,
        java.time.LocalDate lockedAssignedProductionDate,
        String lockedAssignedWindowLabel,
        Duration estimatedProductionDuration,
        boolean fallbackEstimateUsed
) {
    public OffsetDateTime availabilityAt() {
        return actualFactoryArrivalAt == null ? expectedFactoryDropOffWindowEnd : actualFactoryArrivalAt;
    }

    public boolean physicallyAtFactory() {
        return actualFactoryArrivalAt != null
                || lifecycleStatus == LifecycleStatus.AT_FACTORY
                || lifecycleStatus == LifecycleStatus.IN_PRODUCTION
                || lifecycleStatus == LifecycleStatus.READY_FOR_PICKUP;
    }
}
