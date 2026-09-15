package pt.rucodel.productionplanning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.*;
import pt.rucodel.productionplanning.dto.AdminDashboardResponse;
import pt.rucodel.productionplanning.dto.PlanResponse;
import pt.rucodel.productionplanning.dto.RequestResponse;
import pt.rucodel.productionplanning.entity.*;
import pt.rucodel.productionplanning.exception.EntityNotFoundException;
import pt.rucodel.productionplanning.exception.InvalidRequestException;
import pt.rucodel.productionplanning.integration.ProductionDataPort;
import pt.rucodel.productionplanning.mapper.ApiMapper;
import pt.rucodel.productionplanning.repository.ProductionPlanItemRepository;
import pt.rucodel.productionplanning.repository.ProductionPlanRepository;
import pt.rucodel.productionplanning.repository.WheelIntakeRequestRepository;
import pt.rucodel.productionplanning.security.AuthenticatedUser;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductionPlanService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductionPlanService.class);
    private static final EnumSet<LifecycleStatus> CLOSED_STATUSES = EnumSet.of(
            LifecycleStatus.PICKED_UP_FROM_FACTORY,
            LifecycleStatus.CANCELLED
    );

    private final ProductionPlanRepository plans;
    private final ProductionPlanItemRepository planItems;
    private final WheelIntakeRequestRepository requests;
    private final ProductionSettingsService settingsService;
    private final PlanningEngine planningEngine;
    private final ProductionDataPort productionData;
    private final ApiMapper mapper;
    private final Clock clock;
    private final ZoneId businessZone;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    private final DashboardEventPublisher dashboardEventPublisher;
    private final CapacityAlertService capacityAlertService;

    public ProductionPlanService(ProductionPlanRepository plans, ProductionPlanItemRepository planItems,
                                 WheelIntakeRequestRepository requests, ProductionSettingsService settingsService,
                                 PlanningEngine planningEngine, ProductionDataPort productionData, ApiMapper mapper,
                                 Clock clock, AppProperties appProperties, JdbcTemplate jdbcTemplate,
                                 AuditService auditService, DashboardEventPublisher dashboardEventPublisher,
                                 CapacityAlertService capacityAlertService) {
        this.plans = plans;
        this.planItems = planItems;
        this.requests = requests;
        this.settingsService = settingsService;
        this.planningEngine = planningEngine;
        this.productionData = productionData;
        this.mapper = mapper;
        this.clock = clock;
        this.businessZone = ZoneId.of(appProperties.timezone());
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.dashboardEventPublisher = dashboardEventPublisher;
        this.capacityAlertService = capacityAlertService;
    }

    @Transactional(readOnly = true)
    public PlanResponse getLatest(LocalDate date) {
        ProductionPlanEntity plan = plans.findFirstByPlanningDateOrderByVersionNumberDesc(date)
                .orElseThrow(() -> new EntityNotFoundException("Production plan was not found."));
        return mapper.toPlan(plan, planItems.findByPlanIdOrderByPriorityScoreAsc(plan.getId()));
    }

    @Transactional
    public PlanResponse getOrGenerate(LocalDate date) {
        return plans.findFirstByPlanningDateOrderByVersionNumberDesc(date)
                .filter(plan -> !plan.isRequiresRecalculation())
                .map(plan -> mapper.toPlan(plan, planItems.findByPlanIdOrderByPriorityScoreAsc(plan.getId())))
                .orElseGet(() -> generate(date, GenerationTrigger.AUTOMATIC_RECALCULATION, "SYSTEM"));
    }

    @Transactional
    public PlanResponse generate(LocalDate date, GenerationTrigger trigger, AuthenticatedUser user) {
        return generate(date, trigger, user.actorLabel());
    }

    @Transactional
    public PlanResponse generate(LocalDate date, GenerationTrigger trigger, String actor) {
        if (!tryAcquirePlanningLock(date)) {
            throw new InvalidRequestException("PLAN_LOCKED", "A production plan is already being generated for this date.");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        DailyProductionSettingsEntity effectiveSettings = settingsService.effectiveSettings(date);
        ProductionPlanEntity previous = plans.findFirstByPlanningDateOrderByVersionNumberDesc(date).orElse(null);
        Map<UUID, ProductionPlanItemEntity> previousItems = previous == null
                ? Map.of()
                : planItems.findByPlanIdOrderByPriorityScoreAsc(previous.getId()).stream()
                .collect(Collectors.toMap(item -> item.getRequest().getId(), Function.identity(), (a, b) -> a));

        List<WheelIntakeRequestEntity> openRequests = requests.findOpenRequestsForPlanning(CLOSED_STATUSES);
        PlanningResult result = planningEngine.generate(
                date,
                now,
                toPlanningSettings(effectiveSettings),
                openRequests.stream()
                        .map(request -> toWorkItem(request, previousItems.get(request.getId()),
                                effectiveSettings.getFallbackMinutesPerWheel()))
                        .toList(),
                businessZone
        );

        plans.clearCurrentPlan(date);
        ProductionPlanEntity plan = new ProductionPlanEntity();
        plan.setPlanningDate(date);
        plan.setVersionNumber(plans.findMaxVersion(date) + 1);
        plan.setCurrentPlan(true);
        plan.setGeneratedAt(now);
        plan.setGenerationTrigger(trigger);
        plan.setCapacityUsed(result.capacityUsed());
        plan.setTargetUsed(result.targetUsed());
        plan.setMinimumTargetSnapshot(result.targetUsed());
        plan.setMaximumTargetSnapshot(effectiveSettings.getDailyCapacity());
        plan.setTotalKnownWheels(result.totalKnownWheels());
        plan.setTotalPlanned(result.accountingTotals().plannedWheels());
        plan.setTotalWaitingForArrival(result.accountingTotals().waitingForArrival());
        plan.setTotalFutureWorkload(result.accountingTotals().futureWorkload());
        plan.setTotalAtRisk(result.accountingTotals().atRisk());
        plan.setTotalOverCapacity(result.accountingTotals().capacityOverflow());
        plan.setTotalCompleted(0);
        plan.setTotalRemaining(result.accountingTotals().plannedWheels());
        plan.setOvertimeQuantity(result.accountingTotals().capacityOverflow());
        plan.setBelowMinimumQuantity(Math.max(result.targetUsed() - result.accountingTotals().plannedWheels(), 0));
        plan.setCarriedOverQuantity(0);
        plan.setAdvancedQuantity(0);
        plan.setFallbackEstimatesUsed(result.fallbackEstimatesUsed());
        plan.setRequiresRecalculation(false);
        plan.setWarning(result.warning());
        ProductionPlanEntity savedPlan = plans.save(plan);
        List<ProductionPlanItemEntity> savedItems = planItems.saveAll(result.items().stream()
                .map(item -> toPlanItem(savedPlan, item, openRequests))
                .toList());
        auditService.record(savedPlan.getId(), null, "PLAN_GENERATED", actor,
                "Generated plan version " + savedPlan.getVersionNumber() + " for " + date + " using trigger " + trigger + ".");
        capacityAlertService.recalculate(date);
        dashboardEventPublisher.publishPlanUpdated(date);
        return mapper.toPlan(savedPlan, savedItems);
    }

    @Transactional
    public void ensurePlan(LocalDate date, GenerationTrigger trigger) {
        boolean missingOrDirty = plans.findFirstByPlanningDateOrderByVersionNumberDesc(date)
                .map(ProductionPlanEntity::isRequiresRecalculation)
                .orElse(true);
        if (missingOrDirty) {
            generate(date, trigger, "SYSTEM");
        }
    }

    @Transactional
    public AdminDashboardResponse dashboard(LocalDate date) {
        PlanResponse plan = plans.findFirstByPlanningDateOrderByVersionNumberDesc(date)
                .map(entity -> mapper.toPlan(entity, planItems.findByPlanIdOrderByPriorityScoreAsc(entity.getId())))
                .orElse(null);
        DailyProductionSettingsEntity settings = settingsService.effectiveSettings(date);
        List<WheelIntakeRequestEntity> openRequests = requests.findOpenRequestsForPlanning(CLOSED_STATUSES);
        List<RequestResponse> expectedToday = openRequests.stream()
                .filter(request -> request.getActualFactoryArrivalAt() == null)
                .filter(request -> request.getExpectedFactoryDropOffWindowEnd().atZoneSameInstant(businessZone).toLocalDate().equals(date))
                .sorted(Comparator.comparing(WheelIntakeRequestEntity::getExpectedFactoryDropOffWindowStart))
                .map(mapper::toRequest)
                .toList();
        List<RequestResponse> futureWorkload = openRequests.stream()
                .filter(request -> request.getExpectedFactoryDropOffWindowEnd().atZoneSameInstant(businessZone).toLocalDate().isAfter(date)
                        || request.getRequestedFactoryPickupWindowStart().atZoneSameInstant(businessZone).toLocalDate().isAfter(date))
                .sorted(Comparator.comparing(WheelIntakeRequestEntity::getRequestedFactoryPickupWindowStart))
                .map(mapper::toRequest)
                .toList();
        int wheelsAtFactory = sumByStatus(openRequests, LifecycleStatus.ARRIVED_AT_FACTORY, LifecycleStatus.IN_PRODUCTION, LifecycleStatus.READY_FOR_PICKUP);
        int wheelsInProduction = sumByStatus(openRequests, LifecycleStatus.IN_PRODUCTION);
        int wheelsReady = sumByStatus(openRequests, LifecycleStatus.READY_FOR_PICKUP);
        int planned = plan == null ? 0 : plan.totalPlanned();
        int atRisk = plan == null ? 0 : plan.totalAtRisk();
        int overflow = plan == null ? 0 : plan.totalOverCapacity();
        Map<String, List<pt.rucodel.productionplanning.dto.PlanItemResponse>> grouped = plan == null
                ? Map.of()
                : plan.items().stream()
                .collect(Collectors.groupingBy(
                        item -> item.assignedWindowLabel() == null ? "Sem janela" : item.assignedWindowLabel(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<String> risks = plan == null ? List.of("Ainda não existe plano para a data selecionada.") : plan.items().stream()
                .filter(item -> item.riskClassification() != RiskClassification.ON_TRACK)
                .map(item -> item.customerName() + ": " + item.riskClassification() + " - " + item.priorityExplanation())
                .toList();
        return new AdminDashboardResponse(
                date,
                settings.getDailyTarget(),
                settings.getDailyCapacity(),
                planned,
                wheelsAtFactory,
                expectedToday.stream().mapToInt(RequestResponse::expectedWheelQuantity).sum(),
                wheelsInProduction,
                wheelsReady,
                atRisk,
                overflow,
                Math.max(settings.getDailyCapacity() - (plan == null ? 0 : plan.capacityUsed()), 0),
                plan == null ? null : plan.generatedAt(),
                plan,
                grouped,
                expectedToday,
                risks,
                futureWorkload
        );
    }

    private boolean tryAcquirePlanningLock(LocalDate date) {
        try {
            Boolean result = jdbcTemplate.queryForObject(
                    "select pg_try_advisory_xact_lock(hashtext(?))",
                    Boolean.class,
                    "production-planning:" + date
            );
            return Boolean.TRUE.equals(result);
        } catch (DataAccessException ex) {
            LOGGER.debug("PostgreSQL advisory lock unavailable; continuing with local test/runtime lock only", ex);
            return true;
        }
    }

    private PlanningSettings toPlanningSettings(DailyProductionSettingsEntity settings) {
        return new PlanningSettings(
                settings.getDailyCapacity(),
                settings.getDailyTarget(),
                settings.getFallbackMinutesPerWheel(),
                settings.getTimeWindows().stream()
                        .map(window -> new PlanningTimeWindow(window.getLabel(), window.getCutoffTime(), window.getSortOrder()))
                        .toList()
        );
    }

    private PlanningWorkItem toWorkItem(WheelIntakeRequestEntity request, ProductionPlanItemEntity previousItem,
                                        int fallbackMinutesPerWheel) {
        int quantity = request.getActualReceivedWheelQuantity() == null
                ? request.getExpectedWheelQuantity()
                : request.getActualReceivedWheelQuantity();
        boolean ready = request.getLifecycleStatus() == LifecycleStatus.READY_FOR_PICKUP;
        java.util.Optional<Duration> historicalEstimate = productionData.historicalDurationEstimate(quantity);
        Duration estimate = ready
                ? Duration.ZERO
                : historicalEstimate.orElse(Duration.ofMinutes((long) quantity * Math.max(1, fallbackMinutesPerWheel)));
        boolean fallback = !ready && historicalEstimate.isEmpty();
        return new PlanningWorkItem(
                request.getId(),
                request.getCustomerNameSnapshot(),
                request.getDriver().getName(),
                quantity,
                request.getCreatedAt(),
                request.getExpectedFactoryDropOffWindowStart(),
                request.getExpectedFactoryDropOffWindowEnd(),
                request.getActualFactoryArrivalAt(),
                request.getRequestedFactoryPickupWindowStart(),
                request.getLifecycleStatus(),
                request.getManualPriority(),
                request.isPlanningLocked(),
                previousItem == null ? null : previousItem.getAssignedProductionDate(),
                previousItem == null ? null : previousItem.getAssignedWindowLabel(),
                estimate,
                fallback
        );
    }

    private ProductionPlanItemEntity toPlanItem(ProductionPlanEntity plan, PlanningItemResult result,
                                               List<WheelIntakeRequestEntity> sourceRequests) {
        WheelIntakeRequestEntity request = sourceRequests.stream()
                .filter(candidate -> candidate.getId().equals(result.requestId()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Planning request was not found."));
        ProductionPlanItemEntity entity = new ProductionPlanItemEntity();
        entity.setPlan(plan);
        entity.setRequest(request);
        entity.setCustomerName(result.customerName());
        entity.setDriverName(result.driverName());
        entity.setQuantity(result.quantity());
        entity.setCompletedQuantity(0);
        entity.setRemainingQuantity(result.quantity());
        entity.setRequestTotalQuantity(request.getActualReceivedWheelQuantity() == null
                ? request.getExpectedWheelQuantity()
                : request.getActualReceivedWheelQuantity());
        entity.setRequestRemainingQuantity(Math.max(entity.getRequestTotalQuantity() - request.getCompletedWheelQuantity(), 0));
        entity.replaceWheelQuantities(request.wheelQuantityMap());
        entity.setAvailabilityAt(result.availabilityAt());
        entity.setRequiredReadyAt(result.requiredReadyAt());
        entity.setAssignedProductionDate(result.assignedProductionDate());
        entity.setAssignedWindowLabel(result.assignedWindowLabel());
        entity.setAvailabilityClassification(result.availabilityClassification());
        entity.setRiskClassification(result.riskClassification());
        entity.setPriorityScore(result.priorityScore());
        entity.setPriorityExplanation(result.priorityExplanation());
        entity.setManuallyPrioritised(result.manuallyPrioritised());
        entity.setLocked(result.locked());
        entity.setCarriedOver(false);
        entity.setAdvancedFromFuture(false);
        entity.setLineStatus(pt.rucodel.productionplanning.domain.ProductionPlanLineStatus.PLANNED);
        entity.setCreatedAt(OffsetDateTime.now(clock));
        return entity;
    }

    private int sumByStatus(List<WheelIntakeRequestEntity> requests, LifecycleStatus... statuses) {
        EnumSet<LifecycleStatus> statusSet = EnumSet.noneOf(LifecycleStatus.class);
        statusSet.addAll(List.of(statuses));
        return requests.stream()
                .filter(request -> statusSet.contains(request.getLifecycleStatus()))
                .mapToInt(request -> request.getActualReceivedWheelQuantity() == null
                        ? request.getExpectedWheelQuantity()
                        : request.getActualReceivedWheelQuantity())
                .sum();
    }
}
