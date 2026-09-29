package pt.rucodel.productionplanning.mapper;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import pt.rucodel.productionplanning.domain.LifecycleStatus;
import pt.rucodel.productionplanning.domain.ProductionPlanLineStatus;
import pt.rucodel.productionplanning.domain.WheelType;
import pt.rucodel.productionplanning.dto.*;
import pt.rucodel.productionplanning.entity.*;
import pt.rucodel.productionplanning.repository.ProductionPlanItemReconciliationRepository;

import java.util.Arrays;
import java.util.List;

@Component
public class ApiMapper {
    private final pt.rucodel.productionplanning.service.WheelQuantityService wheelQuantities;
    private final ProductionPlanItemReconciliationRepository reconciliations;

    public ApiMapper(pt.rucodel.productionplanning.service.WheelQuantityService wheelQuantities,
                     ProductionPlanItemReconciliationRepository reconciliations) {
        this.wheelQuantities = wheelQuantities;
        this.reconciliations = reconciliations;
    }

    public DriverResponse toDriver(DriverEntity entity) {
        return new DriverResponse(entity.getId(), entity.getDriverCode(), entity.getExternalId(),
                entity.getRucofiId(), entity.getName(), entity.isActive(), entity.getVersion());
    }

    public CustomerResponse toCustomer(CustomerReferenceEntity entity) {
        return new CustomerResponse(
                entity.getId(),
                entity.getCustomerCode(),
                entity.getCustomerNumber(),
                entity.getExternalId(),
                entity.getExternalSystem(),
                entity.getExternalCustomerId(),
                entity.getName(),
                entity.getTaxIdentifier(),
                entity.getCountryCode(),
                entity.getLocality(),
                entity.getStatus() == null ? null : entity.getStatus().name(),
                entity.isActive(),
                entity.getVersion()
        );
    }

    public RequestResponse toRequest(WheelIntakeRequestEntity entity) {
        return toRequest(entity, null, activeReconciliation(entity, null));
    }

    private RequestResponse baseRequest(WheelIntakeRequestEntity entity) {
        Integer actual = entity.getActualReceivedWheelQuantity();
        return new RequestResponse(
                entity.getId(),
                entity.getRequestCode(),
                entity.getSource(),
                entity.getExternalSourceReference(),
                entity.getExternalMessageId(),
                entity.getCustomer() == null ? null : entity.getCustomer().getId(),
                entity.getCustomerExternalId(),
                entity.getCustomerNameSnapshot(),
                entity.getDriver() == null ? null : entity.getDriver().getId(),
                entity.getDriver() == null ? null : entity.getDriver().getName(),
                entity.getSubmittedByIdentity() == null ? null : entity.getSubmittedByIdentity().getId(),
                wheelQuantities.toDto(entity.wheelQuantityMap()),
                entity.getExpectedWheelQuantity(),
                entity.getExpectedWheelQuantity(),
                actual,
                actual != null && actual != entity.getExpectedWheelQuantity(),
                entity.isQuantityDiscrepancyAcknowledged(),
                entity.getExpectedFactoryDropOffWindowStart(),
                entity.getExpectedFactoryDropOffWindowEnd(),
                entity.getRequestedFactoryPickupWindowStart(),
                entity.getRequestedFactoryPickupWindowEnd(),
                entity.getActualFactoryArrivalAt(),
                entity.getArrivalConfirmedAt(),
                entity.getArrivalConfirmedBy(),
                entity.getArrivalConfirmationSource(),
                entity.getActualPickupFromFactoryAt(),
                entity.getRucopiProductionJobId(),
                entity.getNotes(),
                entity.getLifecycleStatus(),
                entity.getManualPriority(),
                entity.isPlanningLocked(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null
        );
    }

    public RequestResponse toRequest(WheelIntakeRequestEntity entity, ProductionPlanItemEntity item) {
        return toRequest(entity, item, activeReconciliation(entity, item));
    }

    private RequestResponse toRequest(WheelIntakeRequestEntity entity, ProductionPlanItemEntity item,
                                      ProductionPlanItemReconciliationEntity activeReconciliation) {
        RequestResponse base = baseRequest(entity);
        ProductionPlanItemEntity productionItem = item != null
                ? item
                : activeReconciliation == null ? null : activeReconciliation.getProductionPlanItem();
        ReopenClosureInfo reopen = reopenClosureInfo(entity, productionItem, activeReconciliation);
        return new RequestResponse(
                base.id(),
                base.requestCode(),
                base.source(),
                base.externalSourceReference(),
                base.externalMessageId(),
                base.customerId(),
                base.customerExternalId(),
                base.customerNameSnapshot(),
                base.driverId(),
                base.driverName(),
                base.submittedByIdentityId(),
                base.wheelQuantities(),
                base.totalQuantity(),
                base.expectedWheelQuantity(),
                base.actualReceivedWheelQuantity(),
                base.quantityDiscrepancy(),
                base.quantityDiscrepancyAcknowledged(),
                base.expectedFactoryDropOffWindowStart(),
                base.expectedFactoryDropOffWindowEnd(),
                base.requestedFactoryPickupWindowStart(),
                base.requestedFactoryPickupWindowEnd(),
                base.actualFactoryArrivalAt(),
                base.arrivalConfirmedAt(),
                base.arrivalConfirmedBy(),
                base.arrivalConfirmationSource(),
                base.actualPickupFromFactoryAt(),
                base.rucopiProductionJobId(),
                base.notes(),
                base.lifecycleStatus(),
                base.manualPriority(),
                base.planningLocked(),
                base.createdAt(),
                base.updatedAt(),
                base.version(),
                productionItem == null ? null : productionItem.getPlan().getPlanningDate(),
                productionItem == null ? null : productionItem.getId(),
                productionItem == null ? null : productionItem.getVersion(),
                productionItem == null ? null : productionItem.getLineStatus(),
                productionItem == null ? null : lineWheelQuantities(productionItem),
                productionItem == null ? null : productionItem.getQuantity(),
                productionItem == null ? null : productionItem.getCompletedQuantity(),
                productionItem == null ? null : productionItem.getRemainingQuantity(),
                productionItem == null ? null : productionItem.getClosedAt(),
                productionItem == null ? null : productionItem.getClosedBy(),
                activeReconciliation == null ? null : activeReconciliation.getId(),
                activeReconciliation == null ? null : activeReconciliation.getStatus(),
                reopen.canReopenClosure(),
                reopen.reopenBlockReason()
        );
    }

    private ProductionPlanItemReconciliationEntity activeReconciliation(WheelIntakeRequestEntity entity,
                                                                        ProductionPlanItemEntity item) {
        if (item != null) {
            return reconciliations.findFirstByProductionPlanItemIdAndRevertedFalseOrderByRevisionDesc(item.getId())
                    .orElse(null);
        }
        List<ProductionPlanItemReconciliationEntity> active = reconciliations
                .findActiveByRequestIdOrderByPlanningDateDescRevisionDesc(entity.getId());
        return active.isEmpty() ? null : active.getFirst();
    }

    private ReopenClosureInfo reopenClosureInfo(WheelIntakeRequestEntity entity, ProductionPlanItemEntity item,
                                                ProductionPlanItemReconciliationEntity activeReconciliation) {
        if (activeReconciliation == null) {
            if ((item != null && isClosedLine(item.getLineStatus())) || entity.getLifecycleStatus() == LifecycleStatus.READY_FOR_PICKUP) {
                return new ReopenClosureInfo(false, "O pedido não possui histórico de fecho suficiente para reabertura.");
            }
            return new ReopenClosureInfo(false, null);
        }
        ProductionPlanItemEntity reconciliationItem = activeReconciliation.getProductionPlanItem();
        if (!isClosedLine(reconciliationItem.getLineStatus())) {
            return new ReopenClosureInfo(false, "O fecho já foi reaberto ou a linha não está fechada.");
        }
        if (!reconciliations.findActiveLaterReconciliations(entity.getId(), activeReconciliation.getPlanningDate()).isEmpty()) {
            return new ReopenClosureInfo(false, "Existem fechos posteriores relacionados. Reabra primeiro os dias posteriores.");
        }
        return new ReopenClosureInfo(true, null);
    }

    private boolean isClosedLine(ProductionPlanLineStatus status) {
        return status == ProductionPlanLineStatus.CLOSED_COMPLETE || status == ProductionPlanLineStatus.CLOSED_PARTIAL;
    }

    private record ReopenClosureInfo(boolean canReopenClosure, String reopenBlockReason) {
    }

    public DailySettingsResponse toSettings(DailyProductionSettingsEntity entity) {
        return new DailySettingsResponse(
                entity.getId(),
                entity.getSettingsKey(),
                entity.getSettingsDate(),
                entity.getDailyCapacity(),
                entity.getDailyTarget(),
                entity.getFallbackMinutesPerWheel(),
                entity.getDailyTarget() > entity.getDailyCapacity(),
                entity.getTimeWindows().stream()
                        .map(window -> new TimeWindowDto(window.getLabel(), window.getCutoffTime(), window.getSortOrder()))
                        .toList(),
                entity.getVersion()
        );
    }

    public PlanItemResponse toPlanItem(ProductionPlanItemEntity entity) {
        return new PlanItemResponse(
                entity.getId(),
                entity.getRequest().getId(),
                entity.getCustomerName(),
                entity.getDriverName(),
                lineWheelQuantities(entity),
                entity.getQuantity(),
                entity.getAvailabilityAt(),
                entity.getRequiredReadyAt(),
                entity.getAssignedProductionDate(),
                entity.getAssignedWindowLabel(),
                entity.getAvailabilityClassification(),
                entity.getRiskClassification(),
                entity.getPriorityScore(),
                entity.getPriorityExplanation(),
                entity.isManuallyPrioritised(),
                entity.isLocked()
        );
    }

    private List<PlanLineWheelQuantityResponse> lineWheelQuantities(ProductionPlanItemEntity entity) {
        return Arrays.stream(WheelType.values())
                .map(type -> entity.getWheelQuantities().stream()
                        .filter(quantity -> quantity.getWheelType() == type)
                        .findFirst()
                        .map(quantity -> new PlanLineWheelQuantityResponse(type, type.label(),
                                quantity.getPlannedQuantity(),
                                quantity.getCompletedQuantity(),
                                quantity.getRemainingQuantity()))
                        .orElse(new PlanLineWheelQuantityResponse(type, type.label(), 0, 0, 0)))
                .toList();
    }

    public PlanResponse toPlan(ProductionPlanEntity entity, java.util.List<ProductionPlanItemEntity> items) {
        return new PlanResponse(
                entity.getId(),
                entity.getPlanningDate(),
                entity.getVersionNumber(),
                entity.getGeneratedAt(),
                entity.getGenerationTrigger(),
                entity.getCapacityUsed(),
                entity.getTargetUsed(),
                entity.getMinimumTargetSnapshot(),
                entity.getMaximumTargetSnapshot(),
                entity.getTotalKnownWheels(),
                entity.getTotalPlanned(),
                entity.getTotalCompleted(),
                entity.getTotalRemaining(),
                entity.getTotalWaitingForArrival(),
                entity.getTotalFutureWorkload(),
                entity.getTotalAtRisk(),
                entity.getTotalOverCapacity(),
                entity.getOvertimeQuantity(),
                entity.getOvertimeQuantity() > 0,
                entity.isFallbackEstimatesUsed(),
                entity.isRequiresRecalculation(),
                entity.getWarning(),
                items.stream().map(this::toPlanItem).toList()
        );
    }

    public AuditEventResponse toAudit(PlanningAuditEventEntity entity) {
        return new AuditEventResponse(
                entity.getId(),
                entity.getPlanId(),
                entity.getRequestId(),
                entity.getEventType(),
                entity.getActor(),
                entity.getDetail(),
                entity.getCreatedAt()
        );
    }

    public StatusHistoryResponse toStatusHistory(RequestStatusHistoryEntity entity) {
        return new StatusHistoryResponse(
                entity.getId(),
                entity.getPreviousStatus(),
                entity.getNewStatus(),
                entity.getChangedAt(),
                entity.getActorUserId(),
                entity.getActorLabel(),
                entity.getReason()
        );
    }

    public <T, R> PageResponse<R> toPage(Page<T> page, java.util.function.Function<T, R> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
