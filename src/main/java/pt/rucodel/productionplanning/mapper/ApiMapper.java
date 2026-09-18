package pt.rucodel.productionplanning.mapper;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import pt.rucodel.productionplanning.domain.WheelType;
import pt.rucodel.productionplanning.dto.*;
import pt.rucodel.productionplanning.entity.*;

import java.util.Arrays;
import java.util.List;

@Component
public class ApiMapper {
    private final pt.rucodel.productionplanning.service.WheelQuantityService wheelQuantities;

    public ApiMapper(pt.rucodel.productionplanning.service.WheelQuantityService wheelQuantities) {
        this.wheelQuantities = wheelQuantities;
    }

    public DriverResponse toDriver(DriverEntity entity) {
        return new DriverResponse(entity.getId(), entity.getExternalId(), entity.getName(), entity.isActive(), entity.getVersion());
    }

    public CustomerResponse toCustomer(CustomerReferenceEntity entity) {
        return new CustomerResponse(entity.getId(), entity.getExternalId(), entity.getName(), entity.isActive(), entity.getVersion());
    }

    public RequestResponse toRequest(WheelIntakeRequestEntity entity) {
        Integer actual = entity.getActualReceivedWheelQuantity();
        return new RequestResponse(
                entity.getId(),
                entity.getSource(),
                entity.getExternalSourceReference(),
                entity.getExternalMessageId(),
                entity.getCustomer().getId(),
                entity.getCustomerExternalId(),
                entity.getCustomerNameSnapshot(),
                entity.getDriver().getId(),
                entity.getDriver().getName(),
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
                entity.getActualPickupFromFactoryAt(),
                entity.getRucopiProductionJobId(),
                entity.getNotes(),
                entity.getLifecycleStatus(),
                entity.getManualPriority(),
                entity.isPlanningLocked(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
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
                entity.getTotalKnownWheels(),
                entity.getTotalPlanned(),
                entity.getTotalWaitingForArrival(),
                entity.getTotalFutureWorkload(),
                entity.getTotalAtRisk(),
                entity.getTotalOverCapacity(),
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
