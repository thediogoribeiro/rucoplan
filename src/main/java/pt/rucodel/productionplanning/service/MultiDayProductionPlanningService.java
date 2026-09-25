package pt.rucodel.productionplanning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.*;
import pt.rucodel.productionplanning.dto.*;
import pt.rucodel.productionplanning.entity.*;
import pt.rucodel.productionplanning.exception.EntityNotFoundException;
import pt.rucodel.productionplanning.exception.InvalidRequestException;
import pt.rucodel.productionplanning.repository.*;
import pt.rucodel.productionplanning.security.AuthenticatedUser;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MultiDayProductionPlanningService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MultiDayProductionPlanningService.class);
    private static final EnumSet<LifecycleStatus> CLOSED_REQUEST_STATUSES = EnumSet.of(
            LifecycleStatus.READY_FOR_PICKUP,
            LifecycleStatus.CANCELLED
    );
    private static final List<WheelType> PLANNING_TYPE_PRIORITY = List.of(
            WheelType.WASHED,
            WheelType.NORMAL,
            WheelType.BIPARTITE
    );
    private static final int BIPARTITE_MINIMUM_BUSINESS_DAYS = 15;
    private static final String BIPARTITE_DEADLINE_REASON = "Prazo mínimo de 15 dias úteis para jantes bipartidas.";

    private final ProductionPlanRepository plans;
    private final ProductionPlanItemRepository planItems;
    private final WheelIntakeRequestRepository requests;
    private final ProductionTargetService targetService;
    private final PlanningRunRepository planningRuns;
    private final AuditService auditService;
    private final DashboardEventPublisher dashboardEvents;
    private final Clock clock;
    private final ZoneId businessZone;
    private final WheelQuantityService wheelQuantityService;
    private final double saturdayProductionFactor;
    private final double sundayProductionFactor;
    private final LocalTime weekdayProductionStart;
    private final LocalTime weekdayProductionEnd;
    private final LocalTime saturdayProductionStart;
    private final LocalTime saturdayProductionEnd;

    public MultiDayProductionPlanningService(ProductionPlanRepository plans, ProductionPlanItemRepository planItems,
                                             WheelIntakeRequestRepository requests, ProductionTargetService targetService,
                                             PlanningRunRepository planningRuns,
                                             AuditService auditService, DashboardEventPublisher dashboardEvents,
                                             Clock clock, AppProperties appProperties, WheelQuantityService wheelQuantityService,
                                             @Value("${app.planning.saturday-production-factor:0.5}") double saturdayProductionFactor,
                                             @Value("${app.planning.sunday-production-factor:0}") double sundayProductionFactor,
                                             @Value("${app.planning.weekday-production-start:04:00}") String weekdayProductionStart,
                                             @Value("${app.planning.weekday-production-end:22:00}") String weekdayProductionEnd,
                                             @Value("${app.planning.saturday-production-start:04:00}") String saturdayProductionStart,
                                             @Value("${app.planning.saturday-production-end:13:00}") String saturdayProductionEnd) {
        this.plans = plans;
        this.planItems = planItems;
        this.requests = requests;
        this.targetService = targetService;
        this.planningRuns = planningRuns;
        this.auditService = auditService;
        this.dashboardEvents = dashboardEvents;
        this.clock = clock;
        this.businessZone = ZoneId.of(appProperties.timezone());
        this.wheelQuantityService = wheelQuantityService;
        this.saturdayProductionFactor = saturdayProductionFactor;
        this.sundayProductionFactor = sundayProductionFactor;
        this.weekdayProductionStart = LocalTime.parse(weekdayProductionStart);
        this.weekdayProductionEnd = LocalTime.parse(weekdayProductionEnd);
        this.saturdayProductionStart = LocalTime.parse(saturdayProductionStart);
        this.saturdayProductionEnd = LocalTime.parse(saturdayProductionEnd);
    }

    @Transactional(readOnly = true)
    public List<DailyProductionPlanResponse> list(LocalDate from, LocalDate to) {
        List<DailyProductionPlanResponse> response = plans.findByPlanningDateBetweenAndCurrentPlanTrueOrderByPlanningDateAsc(from, to).stream()
                .filter(plan -> isProductionDay(plan.getPlanningDate()) || plan.getStatus() == ProductionPlanStatus.CLOSED)
                .map(plan -> toResponse(plan, planItems.findByPlanIdOrderByPriorityScoreAsc(plan.getId())))
                .toList();
        LOGGER.info("Production plans queried from={} to={} plans={} items={}",
                from, to, response.size(), response.stream().mapToInt(plan -> plan.lines().size()).sum());
        return response;
    }

    @Transactional(readOnly = true)
    public DailyProductionPlanResponse getOrGenerate(LocalDate date) {
        if (!isProductionDay(date)) {
            return nonProductionDayResponse(date);
        }
        return plans.findFirstByPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(date)
                .map(plan -> toResponse(plan, planItems.findByPlanIdOrderByPriorityScoreAsc(plan.getId())))
                .orElseGet(() -> emptyProductionDayResponse(date));
    }

    @Transactional(readOnly = true)
    public boolean needsInitialPlanning(LocalDate from) {
        return (requests.countByLifecycleStatus(LifecycleStatus.COMMUNICATED) > 0
                || requests.countByLifecycleStatus(LifecycleStatus.AT_FACTORY) > 0
                || requests.countByLifecycleStatus(LifecycleStatus.IN_PRODUCTION) > 0)
                && plans.countByCurrentPlanTrueAndStatusNot(ProductionPlanStatus.CLOSED) == 0;
    }

    @Transactional
    public DailyProductionPlanResponse recalculate(LocalDate from, GenerationTrigger trigger, AuthenticatedUser user) {
        return recalculate(from, trigger, user.actorLabel());
    }

    @Transactional
    public DailyProductionPlanResponse recalculate(LocalDate from, GenerationTrigger trigger, String actor) {
        if (trigger == GenerationTrigger.MANUAL && plans.findFirstByPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(from)
                .filter(plan -> plan.getStatus() == ProductionPlanStatus.CLOSED)
                .isPresent()) {
            throw new InvalidRequestException("PRODUCTION_PLAN_CLOSED",
                    "O plano de " + from + " já está fechado e não pode ser regenerado.");
        }
        LocalDate to = planningHorizon(from);
        PlanningRunEntity run = startRun(from, to, trigger, actor);
        try {
            generateRange(from, to, trigger, actor);
            run.setStatus(PlanningRunStatus.COMPLETED);
            run.setFinishedAt(OffsetDateTime.now(clock));
            run.setSummary("Planos recalculados de " + from + " a " + to + ".");
            planningRuns.save(run);
            return isProductionDay(from) ? getExisting(from) : nonProductionDayResponse(from);
        } catch (RuntimeException ex) {
            run.setStatus(PlanningRunStatus.FAILED);
            run.setFinishedAt(OffsetDateTime.now(clock));
            run.setSummary(safeSummary(ex.getMessage()));
            planningRuns.save(run);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public DailyProductionPlanResponse reconciliation(LocalDate date) {
        return getExisting(date);
    }

    @Transactional
    public DailyProductionPlanResponse saveReconciliation(LocalDate date, ReconciliationRequest request, AuthenticatedUser user) {
        ProductionPlanEntity plan = currentPlan(date);
        requireNotClosed(plan);
        requireVersion(plan, request.planVersion());
        applyReconciliation(plan, request);
        plan.setStatus(ProductionPlanStatus.AWAITING_RECONCILIATION);
        ProductionPlanEntity saved = plans.saveAndFlush(plan);
        auditService.record(saved.getId(), null, "SHIFT_RECONCILIATION_SAVED", user.actorLabel(),
                "Reconciliation draft saved for " + date + ".");
        return toResponse(saved, planItems.findByPlanIdOrderByPriorityScoreAsc(saved.getId()));
    }

    @Transactional
    public DailyProductionPlanResponse close(LocalDate date, ReconciliationRequest request, AuthenticatedUser user) {
        ProductionPlanEntity plan = currentPlan(date);
        if (plan.getStatus() == ProductionPlanStatus.CLOSED) {
            return toResponse(plan, planItems.findByPlanIdOrderByPriorityScoreAsc(plan.getId()));
        }
        requireVersion(plan, request.planVersion());
        applyReconciliation(plan, request);
        List<ProductionPlanItemEntity> lines = planItems.findByPlanIdOrderByPriorityScoreAsc(plan.getId());
        if (lines.stream().anyMatch(line -> line.getCompletedQuantity() + line.getRemainingQuantity() != line.getQuantity())) {
            throw new InvalidRequestException("Fecho inconsistente: cada linha tem de validar planeado = concluído + pendente.");
        }
        Map<UUID, Map<WheelType, Integer>> completedByRequest = new LinkedHashMap<>();
        for (ProductionPlanItemEntity line : lines) {
            if (line.getRequest().getLifecycleStatus() == LifecycleStatus.COMMUNICATED && line.getCompletedQuantity() > 0) {
                throw new InvalidRequestException("Pedido comunicado não pode ser concluído no fecho sem confirmação de chegada à fábrica.");
            }
            Map<WheelType, Integer> byType = completedByRequest.computeIfAbsent(line.getRequest().getId(), ignored -> wheelQuantityService.empty());
            for (ProductionPlanItemWheelQuantityEntity quantity : line.getWheelQuantities()) {
                byType.put(quantity.getWheelType(), byType.get(quantity.getWheelType()) + quantity.getCompletedQuantity());
            }
        }
        for (Map.Entry<UUID, Map<WheelType, Integer>> entry : completedByRequest.entrySet()) {
            WheelIntakeRequestEntity intake = requests.findById(entry.getKey())
                    .orElseThrow(() -> new EntityNotFoundException("Request was not found."));
            int completedThisClosure = entry.getValue().values().stream().mapToInt(Integer::intValue).sum();
            intake.addCompletedWheelQuantities(entry.getValue());
            if (remainingQuantity(intake) == 0 && intake.getLifecycleStatus() != LifecycleStatus.READY_FOR_PICKUP) {
                intake.setLifecycleStatus(LifecycleStatus.READY_FOR_PICKUP);
                auditService.record(plan.getId(), intake.getId(), "REQUEST_READY_FOR_PICKUP", user.actorLabel(),
                        "Pedido concluído no fecho do turno.");
            } else if (completedThisClosure > 0 && intake.getLifecycleStatus() == LifecycleStatus.AT_FACTORY) {
                intake.setLifecycleStatus(LifecycleStatus.IN_PRODUCTION);
                auditService.record(plan.getId(), intake.getId(), "REQUEST_IN_PRODUCTION", user.actorLabel(),
                        "Pedido parcialmente produzido no fecho do turno.");
            }
            intake.setUpdatedBy(user.actorLabel());
            requests.save(intake);
        }
        plan.setStatus(ProductionPlanStatus.CLOSED);
        plan.setClosedAt(OffsetDateTime.now(clock));
        plan.setClosedBy(user.actorLabel());
        plan.setTotalCompleted(lines.stream().mapToInt(ProductionPlanItemEntity::getCompletedQuantity).sum());
        plan.setTotalRemaining(lines.stream().mapToInt(ProductionPlanItemEntity::getRemainingQuantity).sum());
        ProductionPlanEntity saved = plans.saveAndFlush(plan);
        auditService.record(saved.getId(), null, "SHIFT_CLOSED", user.actorLabel(),
                "Shift closed for " + date + ". Completed " + saved.getTotalCompleted() + ", remaining " + saved.getTotalRemaining() + ".");
        LocalDate next = date.plusDays(1);
        generateRange(next, planningHorizon(next), GenerationTrigger.AUTOMATIC_RECALCULATION, "SYSTEM");
        return toResponse(saved, lines);
    }

    @Transactional
    public DailyProductionPlanResponse reopen(LocalDate date, ReopenPlanRequest request, AuthenticatedUser user) {
        ProductionPlanEntity plan = currentPlan(date);
        requireVersion(plan, request.planVersion());
        if (request.reason() == null || request.reason().isBlank()) {
            throw new InvalidRequestException("É obrigatório indicar o motivo da reabertura.");
        }
        if (plan.getStatus() != ProductionPlanStatus.CLOSED) {
            throw new InvalidRequestException("Só é possível reabrir planos fechados.");
        }
        plan.setStatus(ProductionPlanStatus.AWAITING_RECONCILIATION);
        plan.setReopenedAt(OffsetDateTime.now(clock));
        plan.setReopenedBy(user.actorLabel());
        plan.setReopenReason(request.reason().trim());
        auditService.record(plan.getId(), null, "SHIFT_REOPENED", user.actorLabel(), request.reason().trim());
        generateRange(date.plusDays(1), planningHorizon(date.plusDays(1)), GenerationTrigger.AUTOMATIC_RECALCULATION, "SYSTEM");
        return toResponse(plans.saveAndFlush(plan), planItems.findByPlanIdOrderByPriorityScoreAsc(plan.getId()));
    }

    private void generateRange(LocalDate from, LocalDate to, GenerationTrigger trigger, String actor) {
        List<WheelIntakeRequestEntity> openRequests = requests.findOpenRequestsForPlanning(CLOSED_REQUEST_STATUSES).stream()
                .filter(request -> remainingQuantity(request) > 0)
                .filter(request -> request.getExpectedFactoryDropOffWindowEnd() != null && request.getRequestedFactoryPickupWindowStart() != null)
                .sorted(requestComparator())
                .toList();
        List<PlanningDemand> demands = openRequests.stream()
                .flatMap(request -> demandsFor(request).stream())
                .sorted(demandComparator())
                .toList();

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (plans.findFirstByPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(date)
                    .filter(plan -> plan.getStatus() == ProductionPlanStatus.CLOSED)
                    .isPresent()) {
                continue;
            }
            if (!isProductionDay(date)) {
                plans.clearCurrentPlan(date);
                auditService.record(null, null, "NON_PRODUCTION_DAY_SKIPPED", actor,
                        "Skipped non-production day " + date + ".");
                dashboardEvents.publishPlanUpdated(date);
                continue;
            }
            ProductionTargetConfigurationEntity target = targetService.effectiveFor(date);
            EffectiveDailyTargets effectiveTargets = targetsFor(date, target);
            boolean provisional = previousDayOpen(date);
            List<LineAllocation> allocations = allocateDay(date, demands, effectiveTargets);
            provisional = provisional || allocations.stream().anyMatch(allocation -> allocation.request().getLifecycleStatus() == LifecycleStatus.COMMUNICATED);
            savePlan(date, allocations, effectiveTargets, provisional, trigger, actor);
        }
    }

    private List<LineAllocation> allocateDay(LocalDate date, List<PlanningDemand> demands, EffectiveDailyTargets target) {
        Map<LineKey, MutableLineAllocation> allocations = new LinkedHashMap<>();
        int planned = 0;
        List<PlanningDemand> eligible = eligibleDemands(date, demands);

        for (PlanningDemand demand : eligible.stream().filter(demand -> effectiveDueDate(demand.deadlineAt).isBefore(date)
                || effectiveDueDate(demand.deadlineAt).isEqual(date)).toList()) {
            int quantity = demand.remainingQuantity;
            if (quantity > 0) {
                addAllocation(allocations, demand, quantity, date);
                demand.remainingQuantity -= quantity;
                planned += quantity;
            }
        }

        int desired = desiredDailyLoad(eligible, target, planned);
        for (PlanningDemand demand : eligible) {
            int remaining = demand.remainingQuantity;
            if (remaining <= 0 || planned >= desired) {
                continue;
            }
            int quantity = Math.min(remaining, desired - planned);
            addAllocation(allocations, demand, quantity, date);
            demand.remainingQuantity -= quantity;
            planned += quantity;
        }
        return allocations.values().stream()
                .map(MutableLineAllocation::toAllocation)
                .toList();
    }

    private void addAllocation(Map<LineKey, MutableLineAllocation> allocations, PlanningDemand demand,
                               int quantity, LocalDate date) {
        LineKey key = new LineKey(demand.request.getId(), demand.deadlineAt, carriedOver(demand.request, date),
                effectiveDueDate(demand.deadlineAt).isAfter(date));
        MutableLineAllocation allocation = allocations.computeIfAbsent(key, ignored -> new MutableLineAllocation(
                demand.request,
                wheelQuantityService.empty(),
                demand.materialAvailableAt,
                demand.planningAvailableAt,
                demand.deadlineAt,
                key.carriedOver(),
                key.advancedFromFuture(),
                demand.deadlineAdjustmentReason
        ));
        allocation.quantities.put(demand.type, allocation.quantities.get(demand.type) + quantity);
        if (allocation.deadlineAdjustmentReason == null) {
            allocation.deadlineAdjustmentReason = demand.deadlineAdjustmentReason;
        }
    }

    private int desiredDailyLoad(List<PlanningDemand> eligible, EffectiveDailyTargets target, int alreadyPlanned) {
        int totalEligible = eligible.stream().mapToInt(PlanningDemand::remainingQuantity).sum();
        if (totalEligible <= 0) {
            return alreadyPlanned;
        }
        if (target.regularDailyCapacity() <= 0) {
            return alreadyPlanned + totalEligible;
        }
        return Math.max(alreadyPlanned, Math.min(alreadyPlanned + totalEligible, target.regularDailyCapacity()));
    }

    private List<PlanningDemand> eligibleDemands(LocalDate date, List<PlanningDemand> demands) {
        return demands.stream()
                .filter(demand -> demand.remainingQuantity > 0)
                .filter(demand -> !communicatedArrivalMissed(demand, date))
                .filter(demand -> availableForProductionOn(date, demand.planningAvailableAt))
                .sorted(demandComparator())
                .toList();
    }

    private boolean communicatedArrivalMissed(PlanningDemand demand, LocalDate date) {
        if (demand.request.getLifecycleStatus() != LifecycleStatus.COMMUNICATED) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        LocalDate today = now.atZoneSameInstant(businessZone).toLocalDate();
        return !date.isAfter(today) && demand.request.getExpectedFactoryDropOffWindowEnd().isBefore(now);
    }

    private void savePlan(LocalDate date, List<LineAllocation> allocations, EffectiveDailyTargets target,
                          boolean provisional, GenerationTrigger trigger, String actor) {
        plans.clearCurrentPlan(date);
        ProductionPlanEntity plan = new ProductionPlanEntity();
        plan.setPlanningDate(date);
        plan.setVersionNumber(plans.findMaxVersion(date) + 1);
        plan.setCurrentPlan(true);
        plan.setStatus(provisional ? ProductionPlanStatus.PROVISIONAL : ProductionPlanStatus.PUBLISHED);
        plan.setGeneratedAt(OffsetDateTime.now(clock));
        plan.setGenerationTrigger(trigger);
        int total = allocations.stream().mapToInt(LineAllocation::quantity).sum();
        int overtime = Math.max(total - target.regularDailyCapacity(), 0);
        int belowMinimum = Math.max(target.minimumDailyTarget() - total, 0);
        plan.setCapacityUsed(total);
        plan.setTargetUsed(target.minimumDailyTarget());
        plan.setMinimumTargetSnapshot(target.minimumDailyTarget());
        plan.setMaximumTargetSnapshot(target.regularDailyCapacity());
        plan.setTotalKnownWheels(total);
        plan.setTotalPlanned(total);
        plan.setTotalCompleted(0);
        plan.setTotalRemaining(total);
        plan.setTotalWaitingForArrival(allocations.stream()
                .filter(allocation -> allocation.request().getLifecycleStatus() == LifecycleStatus.COMMUNICATED)
                .mapToInt(LineAllocation::quantity)
                .sum());
        plan.setTotalFutureWorkload(0);
        plan.setTotalAtRisk(overtime);
        plan.setTotalOverCapacity(overtime);
        plan.setOvertimeQuantity(overtime);
        plan.setBelowMinimumQuantity(belowMinimum);
        plan.setCarriedOverQuantity(allocations.stream().filter(LineAllocation::carriedOver).mapToInt(LineAllocation::quantity).sum());
        plan.setAdvancedQuantity(allocations.stream().filter(LineAllocation::advancedFromFuture).mapToInt(LineAllocation::quantity).sum());
        plan.setFallbackEstimatesUsed(false);
        plan.setRequiresRecalculation(false);
        plan.setWarning(warning(total, target, provisional, allocations));
        ProductionPlanEntity saved = plans.saveAndFlush(plan);
        planItems.saveAll(allocations.stream()
                .map(new AllocationToEntity(saved))
                .toList());
        auditService.record(saved.getId(), null, "MULTI_DAY_PLAN_GENERATED", actor,
                "Generated daily distribution for " + date + " with " + total + " wheels.");
        LOGGER.info("Production plan generated planningDate={} trigger={} totalPlanned={} lines={} minimumTarget={} maximumTarget={} overtime={}",
                date, trigger, total, allocations.size(), target.minimumDailyTarget(), target.regularDailyCapacity(), overtime);
        dashboardEvents.publishPlanUpdated(date);
    }

    private String warning(int total, EffectiveDailyTargets target, boolean provisional, List<LineAllocation> allocations) {
        List<String> warnings = new ArrayList<>();
        if (provisional) {
            warnings.add("Plano provisório: existe fecho do turno anterior pendente.");
        }
        if (total < target.minimumDailyTarget()) {
            warnings.add("Target mínimo não atingido: não existe trabalho elegível suficiente.");
        }
        if (total > target.regularDailyCapacity()) {
            warnings.add("Horas extra necessárias: estão planeadas " + total
                    + " jantes para um target máximo de " + target.regularDailyCapacity()
                    + ". Excesso estimado: " + (total - target.regularDailyCapacity()) + " jantes.");
        }
        if (allocations.stream().anyMatch(allocation -> firstProductionDateForAvailability(allocation.planningAvailableAt())
                .isAfter(effectiveDueDate(allocation.deadlineAt())))) {
            warnings.add("Prazo impossível de cumprir com as datas fornecidas.");
        }
        List<LineAllocation> awaitingArrival = allocations.stream()
                .filter(allocation -> allocation.request().getLifecycleStatus() == LifecycleStatus.COMMUNICATED)
                .toList();
        if (!awaitingArrival.isEmpty()) {
            int wheels = awaitingArrival.stream().mapToInt(LineAllocation::quantity).sum();
            warnings.add("Existem " + awaitingArrival.size()
                    + " pedidos planeados cuja chegada ainda não foi confirmada. Total pendente de confirmação: "
                    + wheels + " jantes.");
        }
        if (allocations.stream().map(LineAllocation::deadlineAdjustmentReason).anyMatch(Objects::nonNull)) {
            warnings.add("O prazo das jantes bipartidas foi ajustado devido ao prazo mínimo de 15 dias úteis.");
        }
        return warnings.isEmpty() ? null : String.join(" ", warnings);
    }

    private DailyProductionPlanResponse getExisting(LocalDate date) {
        ProductionPlanEntity plan = currentPlan(date);
        return toResponse(plan, planItems.findByPlanIdOrderByPriorityScoreAsc(plan.getId()));
    }

    private ProductionPlanEntity currentPlan(LocalDate date) {
        return plans.findFirstByPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(date)
                .orElseThrow(() -> new EntityNotFoundException("Production plan was not found."));
    }

    private void applyReconciliation(ProductionPlanEntity plan, ReconciliationRequest request) {
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new InvalidRequestException("É obrigatório validar todas as linhas do plano.");
        }
        Map<UUID, ReconciliationLineRequest> byLine = request.lines().stream()
                .collect(Collectors.toMap(ReconciliationLineRequest::lineId, Function.identity()));
        List<ProductionPlanItemEntity> lines = planItems.findByPlanIdOrderByPriorityScoreAsc(plan.getId());
        if (byLine.size() != lines.size()) {
            throw new InvalidRequestException("É obrigatório validar todas as linhas do plano.");
        }
        for (ProductionPlanItemEntity line : lines) {
            ReconciliationLineRequest update = byLine.get(line.getId());
            if (update == null) {
                throw new InvalidRequestException("É obrigatório validar todas as linhas do plano.");
            }
            if (line.getVersion() != update.version()) {
                throw new InvalidRequestException("OPTIMISTIC_LOCK", "A linha do plano foi alterada por outro utilizador.");
            }
            applyLineReconciliation(line, update);
            line.setOperationalNotes(blankToNull(update.operationalNotes()));
            line.setLineStatus(line.getRemainingQuantity() == 0
                    ? ProductionPlanLineStatus.COMPLETED
                    : line.getCompletedQuantity() == 0 ? ProductionPlanLineStatus.CARRIED_OVER : ProductionPlanLineStatus.PARTIALLY_COMPLETED);
            planItems.save(line);
        }
        plan.setTotalCompleted(lines.stream().mapToInt(ProductionPlanItemEntity::getCompletedQuantity).sum());
        plan.setTotalRemaining(lines.stream().mapToInt(ProductionPlanItemEntity::getRemainingQuantity).sum());
    }

    private void applyLineReconciliation(ProductionPlanItemEntity line, ReconciliationLineRequest update) {
        if (update.wheelQuantities() == null || update.wheelQuantities().isEmpty()) {
            if (update.completedQuantity() == null || update.remainingQuantity() == null) {
                throw new InvalidRequestException("É obrigatório indicar as quantidades concluídas e pendentes.");
            }
            if (update.completedQuantity() + update.remainingQuantity() != line.getQuantity()) {
                throw new InvalidRequestException("Fecho inconsistente: planeado tem de ser igual a concluído mais pendente.");
            }
            line.setCompletedQuantity(update.completedQuantity());
            line.setRemainingQuantity(update.remainingQuantity());
            line.applyWheelQuantityReconciliation(splitAggregateCompletedByPlannedTypes(line, update.completedQuantity()),
                    splitAggregateRemainingByPlannedTypes(line, update.completedQuantity()));
            return;
        }

        Map<WheelType, Integer> completedByType = wheelQuantityService.empty();
        Map<WheelType, Integer> remainingByType = wheelQuantityService.empty();
        Set<WheelType> seen = new HashSet<>();
        for (ReconciliationLineWheelQuantityRequest quantity : update.wheelQuantities()) {
            if (quantity == null || quantity.type() == null || quantity.completedQuantity() == null || quantity.remainingQuantity() == null) {
                throw new InvalidRequestException("É obrigatório indicar tipo, concluído e pendente em cada quantidade.");
            }
            if (!seen.add(quantity.type())) {
                throw new InvalidRequestException("Não é permitido repetir tipos de jantes no fecho.");
            }
            if (quantity.completedQuantity() < 0 || quantity.remainingQuantity() < 0) {
                throw new InvalidRequestException("As quantidades do fecho não podem ser negativas.");
            }
            int plannedForType = line.getWheelQuantities().stream()
                    .filter(existing -> existing.getWheelType() == quantity.type())
                    .mapToInt(ProductionPlanItemWheelQuantityEntity::getPlannedQuantity)
                    .findFirst()
                    .orElse(0);
            if (quantity.completedQuantity() + quantity.remainingQuantity() != plannedForType) {
                throw new InvalidRequestException("Fecho inconsistente: cada tipo tem de validar planeado = concluído + pendente.");
            }
            completedByType.put(quantity.type(), quantity.completedQuantity());
            remainingByType.put(quantity.type(), quantity.remainingQuantity());
        }
        line.applyWheelQuantityReconciliation(completedByType, remainingByType);
        if (line.getCompletedQuantity() + line.getRemainingQuantity() != line.getQuantity()) {
            throw new InvalidRequestException("Fecho inconsistente: planeado tem de ser igual a concluído mais pendente.");
        }
    }

    private Map<WheelType, Integer> splitAggregateCompletedByPlannedTypes(ProductionPlanItemEntity line, int amount) {
        Map<WheelType, Integer> result = wheelQuantityService.empty();
        int remaining = amount;
        for (ProductionPlanItemWheelQuantityEntity quantity : line.getWheelQuantities()) {
            int take = Math.min(quantity.getPlannedQuantity(), remaining);
            result.put(quantity.getWheelType(), take);
            remaining -= take;
            if (remaining == 0) {
                break;
            }
        }
        return result;
    }

    private Map<WheelType, Integer> splitAggregateRemainingByPlannedTypes(ProductionPlanItemEntity line, int completedAmount) {
        Map<WheelType, Integer> completed = splitAggregateCompletedByPlannedTypes(line, completedAmount);
        Map<WheelType, Integer> result = wheelQuantityService.empty();
        for (ProductionPlanItemWheelQuantityEntity quantity : line.getWheelQuantities()) {
            result.put(quantity.getWheelType(),
                    quantity.getPlannedQuantity() - completed.getOrDefault(quantity.getWheelType(), 0));
        }
        return result;
    }

    private boolean previousDayOpen(LocalDate date) {
        return plans.findFirstByPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(date.minusDays(1))
                .map(plan -> plan.getStatus() != ProductionPlanStatus.CLOSED)
                .orElse(false);
    }

    private LocalDate planningHorizon(LocalDate from) {
        LocalDate requestHorizon = requests.findOpenRequestsForPlanning(CLOSED_REQUEST_STATUSES).stream()
                .filter(request -> remainingQuantity(request) > 0)
                .flatMap(request -> demandsFor(request).stream())
                .map(demand -> max(effectiveDueDate(demand.deadlineAt), firstProductionDateForAvailability(demand.planningAvailableAt)))
                .max(LocalDate::compareTo)
                .orElse(from.plusDays(1));
        LocalDate existing = plans.findMaxCurrentPlanningDate();
        LocalDate horizon = existing == null ? requestHorizon : max(requestHorizon, existing);
        return max(horizon, from.plusDays(1));
    }

    private Comparator<WheelIntakeRequestEntity> requestComparator() {
        return Comparator
                .comparing(this::latestEffectiveDueDate)
                .thenComparing(this::dueAt)
                .thenComparing(this::availableAt)
                .thenComparing(WheelIntakeRequestEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(WheelIntakeRequestEntity::getId);
    }

    private Comparator<PlanningDemand> demandComparator() {
        return Comparator
                .comparing((PlanningDemand demand) -> carriedOver(demand.request, demand.planningAvailableAt.atZoneSameInstant(businessZone).toLocalDate()) ? 0 : 1)
                .thenComparing(demand -> demand.deadlineAt)
                .thenComparingInt(demand -> typePriority(demand.type))
                .thenComparing(demand -> demand.materialAvailableAt)
                .thenComparing(demand -> demand.request.getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(demand -> demand.request.getId());
    }

    private int typePriority(WheelType type) {
        int index = PLANNING_TYPE_PRIORITY.indexOf(type);
        return index >= 0 ? index : PLANNING_TYPE_PRIORITY.size();
    }

    private boolean carriedOver(WheelIntakeRequestEntity request, LocalDate date) {
        return !planItems.findClosedCarryOver(
                request.getId(), date, ProductionPlanStatus.CLOSED
        ).isEmpty();
    }

    private LocalDate effectiveDueDate(WheelIntakeRequestEntity request) {
        return effectiveDueDate(latestEffectiveDeadlineAt(request));
    }

    private LocalDate effectiveDueDate(OffsetDateTime due) {
        LocalDate date = due.atZoneSameInstant(businessZone).toLocalDate();
        LocalTime time = due.atZoneSameInstant(businessZone).toLocalTime();
        while (date.isAfter(LocalDate.MIN.plusDays(7))) {
            Optional<ProductionWindow> window = productionWindow(date);
            if (window.isEmpty()) {
                date = date.minusDays(1);
                time = LocalTime.MAX;
                continue;
            }
            if (time.isAfter(window.get().start())) {
                return date;
            }
            date = date.minusDays(1);
            time = LocalTime.MAX;
        }
        return date;
    }

    private boolean availableForProductionOn(LocalDate date, WheelIntakeRequestEntity request) {
        return availableForProductionOn(date, availableAt(request));
    }

    private boolean availableForProductionOn(LocalDate date, OffsetDateTime available) {
        if (!isProductionDay(date)) {
            return false;
        }
        LocalDate availableDate = available.atZoneSameInstant(businessZone).toLocalDate();
        if (availableDate.isBefore(date)) {
            return true;
        }
        if (availableDate.isAfter(date)) {
            return false;
        }
        return productionWindow(date)
                .map(window -> available.atZoneSameInstant(businessZone).toLocalTime().isBefore(window.end()))
                .orElse(false);
    }

    private boolean impossibleDeadline(WheelIntakeRequestEntity request) {
        return demandsFor(request).stream().anyMatch(this::impossibleDeadline);
    }

    private boolean impossibleDeadline(PlanningDemand demand) {
        return firstProductionDateForAvailability(demand.planningAvailableAt).isAfter(effectiveDueDate(demand.deadlineAt));
    }

    private LocalDate firstProductionDateForAvailability(WheelIntakeRequestEntity request) {
        return firstProductionDateForAvailability(availableAt(request));
    }

    private LocalDate firstProductionDateForAvailability(OffsetDateTime available) {
        LocalDate date = available.atZoneSameInstant(businessZone).toLocalDate();
        LocalTime time = available.atZoneSameInstant(businessZone).toLocalTime();
        while (date.isBefore(LocalDate.MAX.minusDays(7))) {
            Optional<ProductionWindow> window = productionWindow(date);
            if (window.isPresent() && time.isBefore(window.get().end())) {
                return date;
            }
            date = date.plusDays(1);
            time = LocalTime.MIN;
        }
        return date;
    }

    private EffectiveDailyTargets targetsFor(LocalDate date, ProductionTargetConfigurationEntity target) {
        double factor = productionFactor(date);
        return new EffectiveDailyTargets(
                (int) Math.floor(target.getMinimumDailyTarget() * factor),
                (int) Math.floor(target.getRegularDailyCapacity() * factor)
        );
    }

    private boolean isProductionDay(LocalDate date) {
        return productionWindow(date).isPresent();
    }

    private double productionFactor(LocalDate date) {
        return productionWindow(date).map(ProductionWindow::factor).orElse(0d);
    }

    private Optional<ProductionWindow> productionWindow(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return sundayProductionFactor > 0
                    ? Optional.of(new ProductionWindow(weekdayProductionStart, weekdayProductionEnd, sundayProductionFactor))
                    : Optional.empty();
        }
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return saturdayProductionFactor > 0
                    ? Optional.of(new ProductionWindow(saturdayProductionStart, saturdayProductionEnd, saturdayProductionFactor))
                    : Optional.empty();
        }
        return Optional.of(new ProductionWindow(weekdayProductionStart, weekdayProductionEnd, 1d));
    }

    private DailyProductionPlanResponse nonProductionDayResponse(LocalDate date) {
        return plans.findFirstByPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(date)
                .filter(plan -> plan.getStatus() == ProductionPlanStatus.CLOSED)
                .map(plan -> toResponse(plan, planItems.findByPlanIdOrderByPriorityScoreAsc(plan.getId())))
                .orElseGet(() -> {
                    return new DailyProductionPlanResponse(
                            UUID.nameUUIDFromBytes(("non-production:" + date).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                            date,
                            0,
                            ProductionPlanStatus.DRAFT,
                            0,
                            0,
                            wheelQuantityService.toDto(wheelQuantityService.empty()),
                            0,
                            0,
                            0,
                            0,
                            0,
                            0,
                            0,
                            0,
                            false,
                            "Domingo não é dia de produção.",
                            OffsetDateTime.now(clock),
                            null,
                            null,
                            GenerationTrigger.AUTOMATIC_RECALCULATION,
                            0,
                            List.of()
                    );
                });
    }

    private DailyProductionPlanResponse emptyProductionDayResponse(LocalDate date) {
        EffectiveDailyTargets target = targetsFor(date, targetService.effectiveFor(date));
        return new DailyProductionPlanResponse(
                UUID.nameUUIDFromBytes(("empty-plan:" + date).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                date,
                0,
                ProductionPlanStatus.DRAFT,
                target.minimumDailyTarget(),
                target.regularDailyCapacity(),
                wheelQuantityService.toDto(wheelQuantityService.empty()),
                0,
                0,
                0,
                target.minimumDailyTarget(),
                0,
                0,
                0,
                0,
                false,
                "Não existem dados de planeamento para apresentar.",
                OffsetDateTime.now(clock),
                null,
                null,
                GenerationTrigger.AUTOMATIC_RECALCULATION,
                0,
                List.of()
        );
    }

    private OffsetDateTime availableAt(WheelIntakeRequestEntity request) {
        return request.getActualFactoryArrivalAt() == null
                ? request.getExpectedFactoryDropOffWindowEnd()
                : request.getActualFactoryArrivalAt();
    }

    private OffsetDateTime dueAt(WheelIntakeRequestEntity request) {
        return request.getRequestedFactoryPickupWindowStart();
    }

    private int totalQuantity(WheelIntakeRequestEntity request) {
        return request.getActualReceivedWheelQuantity() == null
                ? request.getExpectedWheelQuantity()
                : request.getActualReceivedWheelQuantity();
    }

    private int remainingQuantity(WheelIntakeRequestEntity request) {
        return Math.max(totalQuantity(request) - request.getCompletedWheelQuantity(), 0);
    }

    private Map<WheelType, Integer> remainingQuantityByType(WheelIntakeRequestEntity request) {
        Map<WheelType, Integer> result = wheelQuantityService.empty();
        for (WheelType type : WheelType.values()) {
            result.put(type, Math.max(request.wheelQuantity(type) - request.completedWheelQuantity(type), 0));
        }
        return result;
    }

    private List<PlanningDemand> demandsFor(WheelIntakeRequestEntity request) {
        Map<WheelType, Integer> remainingByType = remainingQuantityByType(request);
        List<PlanningDemand> result = new ArrayList<>();
        for (WheelType type : PLANNING_TYPE_PRIORITY) {
            int quantity = remainingByType.getOrDefault(type, 0);
            if (quantity <= 0) {
                continue;
            }
            OffsetDateTime materialAvailableAt = availableAt(request);
            OffsetDateTime deadlineAt = effectiveDeadlineAt(request, type);
            String adjustmentReason = deadlineAdjustmentReason(request, type, deadlineAt);
            OffsetDateTime planningAvailableAt = planningAvailableAt(type, materialAvailableAt, deadlineAt);
            result.add(new PlanningDemand(request, type, materialAvailableAt, planningAvailableAt,
                    deadlineAt, adjustmentReason, quantity));
        }
        return result;
    }

    private OffsetDateTime planningAvailableAt(WheelType type, OffsetDateTime materialAvailableAt, OffsetDateTime deadlineAt) {
        if (type != WheelType.BIPARTITE) {
            return materialAvailableAt;
        }
        LocalDate date = deadlineAt.atZoneSameInstant(businessZone).toLocalDate();
        LocalTime start = productionWindow(date).map(ProductionWindow::start).orElse(LocalTime.MIN);
        OffsetDateTime earliestBipartiteCompletionDay = date.atTime(start).atZone(businessZone).toOffsetDateTime();
        return earliestBipartiteCompletionDay.isAfter(materialAvailableAt) ? earliestBipartiteCompletionDay : materialAvailableAt;
    }

    private OffsetDateTime latestEffectiveDeadlineAt(WheelIntakeRequestEntity request) {
        return demandsFor(request).stream()
                .map(demand -> demand.deadlineAt)
                .max(OffsetDateTime::compareTo)
                .orElseGet(() -> dueAt(request));
    }

    private LocalDate latestEffectiveDueDate(WheelIntakeRequestEntity request) {
        return effectiveDueDate(latestEffectiveDeadlineAt(request));
    }

    private OffsetDateTime effectiveDeadlineAt(WheelIntakeRequestEntity request, WheelType type) {
        return request.getWheelQuantities().stream()
                .filter(quantity -> quantity.getWheelType() == type)
                .findFirst()
                .map(RequestWheelQuantityEntity::getEffectiveDeadlineAt)
                .filter(Objects::nonNull)
                .orElseGet(() -> computedEffectiveDeadlineAt(request, type));
    }

    private String deadlineAdjustmentReason(WheelIntakeRequestEntity request, WheelType type, OffsetDateTime deadlineAt) {
        Optional<RequestWheelQuantityEntity> quantity = request.getWheelQuantities().stream()
                .filter(existing -> existing.getWheelType() == type)
                .findFirst();
        String persisted = quantity.map(RequestWheelQuantityEntity::getDeadlineAdjustmentReason).orElse(null);
        if (persisted != null && !persisted.isBlank()) {
            return persisted;
        }
        if (type == WheelType.BIPARTITE && deadlineAt.isAfter(dueAt(request))) {
            return BIPARTITE_DEADLINE_REASON;
        }
        return null;
    }

    private OffsetDateTime computedEffectiveDeadlineAt(WheelIntakeRequestEntity request, WheelType type) {
        OffsetDateTime requested = dueAt(request);
        if (type != WheelType.BIPARTITE || request.wheelQuantity(type) <= 0) {
            return requested;
        }
        OffsetDateTime minimum = minimumBipartiteDeadline(availableAt(request), requested);
        return minimum.isAfter(requested) ? minimum : requested;
    }

    private OffsetDateTime minimumBipartiteDeadline(OffsetDateTime available, OffsetDateTime requestedDeadline) {
        LocalDate date = available.atZoneSameInstant(businessZone).toLocalDate();
        int remaining = BIPARTITE_MINIMUM_BUSINESS_DAYS;
        while (remaining > 0) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                remaining--;
            }
        }
        return date.atTime(requestedDeadline.atZoneSameInstant(businessZone).toLocalTime())
                .atZone(businessZone)
                .toOffsetDateTime();
    }

    private int total(Map<WheelType, Integer> quantities) {
        return quantities == null ? 0 : wheelQuantityService.total(quantities);
    }

    private Map<WheelType, Integer> sumLines(List<ProductionPlanItemEntity> lines) {
        Map<WheelType, Integer> result = wheelQuantityService.empty();
        for (ProductionPlanItemEntity line : lines) {
            for (ProductionPlanItemWheelQuantityEntity quantity : line.getWheelQuantities()) {
                result.put(quantity.getWheelType(), result.get(quantity.getWheelType()) + quantity.getPlannedQuantity());
            }
        }
        return result;
    }

    private List<PlanLineWheelQuantityResponse> lineWheelQuantities(ProductionPlanItemEntity line) {
        return Arrays.stream(WheelType.values())
                .map(type -> line.getWheelQuantities().stream()
                        .filter(quantity -> quantity.getWheelType() == type)
                        .findFirst()
                        .map(quantity -> new PlanLineWheelQuantityResponse(type, type.label(),
                                quantity.getPlannedQuantity(),
                                quantity.getCompletedQuantity(),
                                quantity.getRemainingQuantity()))
                        .orElse(new PlanLineWheelQuantityResponse(type, type.label(), 0, 0, 0)))
                .toList();
    }

    private void requireNotClosed(ProductionPlanEntity plan) {
        if (plan.getStatus() == ProductionPlanStatus.CLOSED) {
            throw new InvalidRequestException("Planos fechados não podem ser alterados diretamente.");
        }
    }

    private void requireVersion(ProductionPlanEntity plan, Long version) {
        if (version == null || version != plan.getOptimisticVersion()) {
            throw new InvalidRequestException("OPTIMISTIC_LOCK", "O plano foi alterado por outro utilizador.");
        }
    }

    private PlanningRunEntity startRun(LocalDate from, LocalDate to, GenerationTrigger trigger, String actor) {
        PlanningRunEntity run = new PlanningRunEntity();
        run.setTrigger(trigger);
        run.setStatus(PlanningRunStatus.STARTED);
        run.setStartedAt(OffsetDateTime.now(clock));
        run.setAffectedDateFrom(from);
        run.setAffectedDateTo(to);
        run.setActor(actor);
        run.setSummary("Execução iniciada.");
        return planningRuns.saveAndFlush(run);
    }

    private DailyProductionPlanResponse toResponse(ProductionPlanEntity plan, List<ProductionPlanItemEntity> lines) {
        return new DailyProductionPlanResponse(
                plan.getId(),
                plan.getPlanningDate(),
                plan.getVersionNumber(),
                plan.getStatus(),
                plan.getMinimumTargetSnapshot(),
                plan.getMaximumTargetSnapshot(),
                wheelQuantityService.toDto(sumLines(lines)),
                plan.getTotalPlanned(),
                plan.getTotalCompleted(),
                plan.getTotalRemaining(),
                plan.getBelowMinimumQuantity(),
                plan.getOvertimeQuantity(),
                plan.getCarriedOverQuantity(),
                plan.getAdvancedQuantity(),
                plan.getTotalAtRisk(),
                plan.getOvertimeQuantity() > 0,
                plan.getWarning(),
                plan.getGeneratedAt(),
                plan.getClosedAt(),
                plan.getClosedBy(),
                plan.getGenerationTrigger(),
                plan.getOptimisticVersion(),
                lines.stream().map(this::toLineResponse).toList()
        );
    }

    private DailyProductionPlanLineResponse toLineResponse(ProductionPlanItemEntity line) {
        WheelIntakeRequestEntity request = line.getRequest();
        return new DailyProductionPlanLineResponse(
                line.getId(),
                request.getId(),
                request.getRequestCode(),
                request.getCustomer() == null ? null : request.getCustomer().getId(),
                line.getCustomerName(),
                line.getDriverName(),
                request.getSource(),
                line.getPriorityScore().intValue(),
                line.getRequestTotalQuantity(),
                line.getRequestRemainingQuantity(),
                wheelQuantityService.toDto(request.wheelQuantityMap()),
                lineWheelQuantities(line),
                line.getQuantity(),
                line.getCompletedQuantity(),
                line.getRemainingQuantity(),
                line.getAvailabilityAt(),
                line.getRequiredReadyAt(),
                line.getAvailabilityClassification(),
                request.getLifecycleStatus(),
                request.getExpectedFactoryDropOffWindowStart(),
                request.getExpectedFactoryDropOffWindowEnd(),
                request.getRequestedFactoryPickupWindowStart(),
                request.getRequestedFactoryPickupWindowEnd(),
                request.getNotes(),
                line.getOperationalNotes(),
                line.isCarriedOver(),
                line.isAdvancedFromFuture(),
                line.getPriorityExplanation(),
                line.getRiskClassification(),
                line.getLineStatus(),
                line.getVersion()
        );
    }

    private LocalDate max(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safeSummary(String value) {
        String safe = value == null ? "Planning failed." : value;
        return safe.length() > 2000 ? safe.substring(0, 2000) : safe;
    }

    private record EffectiveDailyTargets(int minimumDailyTarget, int regularDailyCapacity) {
    }

    private record ProductionWindow(LocalTime start, LocalTime end, double factor) {
    }

    private static final class PlanningDemand {
        private final WheelIntakeRequestEntity request;
        private final WheelType type;
        private final OffsetDateTime materialAvailableAt;
        private final OffsetDateTime planningAvailableAt;
        private final OffsetDateTime deadlineAt;
        private final String deadlineAdjustmentReason;
        private int remainingQuantity;

        private PlanningDemand(WheelIntakeRequestEntity request, WheelType type, OffsetDateTime materialAvailableAt,
                               OffsetDateTime planningAvailableAt, OffsetDateTime deadlineAt,
                               String deadlineAdjustmentReason, int remainingQuantity) {
            this.request = request;
            this.type = type;
            this.materialAvailableAt = materialAvailableAt;
            this.planningAvailableAt = planningAvailableAt;
            this.deadlineAt = deadlineAt;
            this.deadlineAdjustmentReason = deadlineAdjustmentReason;
            this.remainingQuantity = remainingQuantity;
        }

        private int remainingQuantity() {
            return remainingQuantity;
        }
    }

    private record LineKey(UUID requestId, OffsetDateTime deadlineAt, boolean carriedOver, boolean advancedFromFuture) {
    }

    private static final class MutableLineAllocation {
        private final WheelIntakeRequestEntity request;
        private final Map<WheelType, Integer> quantities;
        private final OffsetDateTime materialAvailableAt;
        private final OffsetDateTime planningAvailableAt;
        private final OffsetDateTime deadlineAt;
        private final boolean carriedOver;
        private final boolean advancedFromFuture;
        private String deadlineAdjustmentReason;

        private MutableLineAllocation(WheelIntakeRequestEntity request, Map<WheelType, Integer> quantities,
                                      OffsetDateTime materialAvailableAt, OffsetDateTime planningAvailableAt,
                                      OffsetDateTime deadlineAt,
                                      boolean carriedOver, boolean advancedFromFuture,
                                      String deadlineAdjustmentReason) {
            this.request = request;
            this.quantities = quantities;
            this.materialAvailableAt = materialAvailableAt;
            this.planningAvailableAt = planningAvailableAt;
            this.deadlineAt = deadlineAt;
            this.carriedOver = carriedOver;
            this.advancedFromFuture = advancedFromFuture;
            this.deadlineAdjustmentReason = deadlineAdjustmentReason;
        }

        private LineAllocation toAllocation() {
            return new LineAllocation(request, quantities, materialAvailableAt, planningAvailableAt, deadlineAt,
                    carriedOver, advancedFromFuture, deadlineAdjustmentReason);
        }
    }

    private record LineAllocation(WheelIntakeRequestEntity request, Map<WheelType, Integer> quantities,
                                  OffsetDateTime materialAvailableAt, OffsetDateTime planningAvailableAt,
                                  OffsetDateTime deadlineAt, boolean carriedOver, boolean advancedFromFuture,
                                  String deadlineAdjustmentReason) {
        int quantity() {
            return quantities.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private class AllocationToEntity implements Function<LineAllocation, ProductionPlanItemEntity> {
        private final ProductionPlanEntity plan;
        private int priority = 1;

        private AllocationToEntity(ProductionPlanEntity plan) {
            this.plan = plan;
        }

        @Override
        public ProductionPlanItemEntity apply(LineAllocation allocation) {
            WheelIntakeRequestEntity request = allocation.request();
            ProductionPlanItemEntity entity = new ProductionPlanItemEntity();
            entity.setPlan(plan);
            entity.setRequest(request);
            entity.setCustomerName(request.getCustomerNameSnapshot());
            entity.setDriverName(request.getDriver().getName());
            entity.setQuantity(allocation.quantity());
            entity.setCompletedQuantity(0);
            entity.setRemainingQuantity(allocation.quantity());
            entity.setRequestTotalQuantity(totalQuantity(request));
            entity.setRequestRemainingQuantity(remainingQuantity(request));
            entity.replaceWheelQuantities(allocation.quantities());
            entity.setAvailabilityAt(allocation.materialAvailableAt());
            entity.setRequiredReadyAt(allocation.deadlineAt());
            entity.setAssignedProductionDate(plan.getPlanningDate());
            entity.setAssignedWindowLabel("Plano diário");
            entity.setAvailabilityClassification(request.getLifecycleStatus() == LifecycleStatus.COMMUNICATED
                    ? AvailabilityClassification.WAITING_FOR_ARRIVAL
                    : request.getActualFactoryArrivalAt() == null
                    ? AvailabilityClassification.TENTATIVE
                    : AvailabilityClassification.CONFIRMED);
            entity.setRiskClassification(firstProductionDateForAvailability(allocation.planningAvailableAt()).isAfter(effectiveDueDate(allocation.deadlineAt()))
                    || effectiveDueDate(allocation.deadlineAt()).isBefore(plan.getPlanningDate())
                    || plan.getOvertimeQuantity() > 0 ? RiskClassification.AT_RISK : RiskClassification.ON_TRACK);
            entity.setPriorityScore(BigDecimal.valueOf(priority++));
            entity.setPriorityExplanation(priorityExplanation(allocation, request));
            entity.setManuallyPrioritised(request.getManualPriority() != null);
            entity.setLocked(false);
            entity.setCarriedOver(allocation.carriedOver());
            entity.setAdvancedFromFuture(allocation.advancedFromFuture());
            entity.setLineStatus(ProductionPlanLineStatus.PLANNED);
            entity.setCreatedAt(OffsetDateTime.now(clock));
            return entity;
        }

        private String priorityExplanation(LineAllocation allocation, WheelIntakeRequestEntity request) {
            List<String> parts = new ArrayList<>();
            if (allocation.carriedOver()) {
                parts.add("Trabalho transportado de dia anterior.");
            }
            if (allocation.advancedFromFuture()) {
                parts.add("Trabalho antecipado para equilibrar a carga e atingir targets.");
            }
            if (firstProductionDateForAvailability(allocation.planningAvailableAt()).isAfter(effectiveDueDate(allocation.deadlineAt()))) {
                parts.add("Prazo impossível de cumprir com as datas fornecidas.");
            }
            if (allocation.deadlineAdjustmentReason() != null) {
                parts.add(allocation.deadlineAdjustmentReason());
            }
            parts.add("Prazo: " + allocation.deadlineAt().atZoneSameInstant(businessZone).toLocalDate() + ".");
            return String.join(" ", parts);
        }
    }
}
