package pt.rucodel.productionplanning.service;

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
    private static final EnumSet<LifecycleStatus> CLOSED_REQUEST_STATUSES = EnumSet.of(
            LifecycleStatus.PICKED_UP_FROM_FACTORY,
            LifecycleStatus.CANCELLED
    );

    private final ProductionPlanRepository plans;
    private final ProductionPlanItemRepository planItems;
    private final WheelIntakeRequestRepository requests;
    private final ProductionTargetService targetService;
    private final PlanningRunRepository planningRuns;
    private final CapacityAlertService capacityAlerts;
    private final AuditService auditService;
    private final DashboardEventPublisher dashboardEvents;
    private final Clock clock;
    private final ZoneId businessZone;
    private final WheelQuantityService wheelQuantityService;

    public MultiDayProductionPlanningService(ProductionPlanRepository plans, ProductionPlanItemRepository planItems,
                                             WheelIntakeRequestRepository requests, ProductionTargetService targetService,
                                             PlanningRunRepository planningRuns, CapacityAlertService capacityAlerts,
                                             AuditService auditService, DashboardEventPublisher dashboardEvents,
                                             Clock clock, AppProperties appProperties, WheelQuantityService wheelQuantityService) {
        this.plans = plans;
        this.planItems = planItems;
        this.requests = requests;
        this.targetService = targetService;
        this.planningRuns = planningRuns;
        this.capacityAlerts = capacityAlerts;
        this.auditService = auditService;
        this.dashboardEvents = dashboardEvents;
        this.clock = clock;
        this.businessZone = ZoneId.of(appProperties.timezone());
        this.wheelQuantityService = wheelQuantityService;
    }

    @Transactional(readOnly = true)
    public List<DailyProductionPlanResponse> list(LocalDate from, LocalDate to) {
        return plans.findByPlanningDateBetweenAndCurrentPlanTrueOrderByPlanningDateAsc(from, to).stream()
                .map(plan -> toResponse(plan, planItems.findByPlanIdOrderByPriorityScoreAsc(plan.getId())))
                .toList();
    }

    @Transactional
    public DailyProductionPlanResponse getOrGenerate(LocalDate date) {
        return plans.findFirstByPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(date)
                .filter(plan -> !plan.isRequiresRecalculation())
                .map(plan -> toResponse(plan, planItems.findByPlanIdOrderByPriorityScoreAsc(plan.getId())))
                .orElseGet(() -> recalculate(date, GenerationTrigger.AUTOMATIC_RECALCULATION, "SYSTEM"));
    }

    @Transactional
    public DailyProductionPlanResponse recalculate(LocalDate from, GenerationTrigger trigger, AuthenticatedUser user) {
        return recalculate(from, trigger, user.actorLabel());
    }

    @Transactional
    public DailyProductionPlanResponse recalculate(LocalDate from, GenerationTrigger trigger, String actor) {
        LocalDate to = planningHorizon(from);
        PlanningRunEntity run = startRun(from, to, trigger, actor);
        try {
            generateRange(from, to, trigger, actor);
            run.setStatus(PlanningRunStatus.COMPLETED);
            run.setFinishedAt(OffsetDateTime.now(clock));
            run.setSummary("Planos recalculados de " + from + " a " + to + ".");
            planningRuns.save(run);
            return getExisting(from);
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
            Map<WheelType, Integer> byType = completedByRequest.computeIfAbsent(line.getRequest().getId(), ignored -> wheelQuantityService.empty());
            for (ProductionPlanItemWheelQuantityEntity quantity : line.getWheelQuantities()) {
                byType.put(quantity.getWheelType(), byType.get(quantity.getWheelType()) + quantity.getCompletedQuantity());
            }
        }
        for (Map.Entry<UUID, Map<WheelType, Integer>> entry : completedByRequest.entrySet()) {
            WheelIntakeRequestEntity intake = requests.findById(entry.getKey())
                    .orElseThrow(() -> new EntityNotFoundException("Request was not found."));
            intake.addCompletedWheelQuantities(entry.getValue());
            if (remainingQuantity(intake) == 0 && intake.getLifecycleStatus() != LifecycleStatus.PICKED_UP_FROM_FACTORY) {
                intake.setLifecycleStatus(LifecycleStatus.READY_FOR_PICKUP);
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
        Map<UUID, Map<WheelType, Integer>> unplanned = openRequests.stream()
                .collect(Collectors.toMap(WheelIntakeRequestEntity::getId, this::remainingQuantityByType,
                        (a, b) -> a, LinkedHashMap::new));

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (plans.findFirstByPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(date)
                    .filter(plan -> plan.getStatus() == ProductionPlanStatus.CLOSED)
                    .isPresent()) {
                continue;
            }
            ProductionTargetConfigurationEntity target = targetService.effectiveFor(date);
            boolean provisional = previousDayOpen(date);
            List<LineAllocation> allocations = allocateDay(date, openRequests, unplanned, target);
            savePlan(date, allocations, target, provisional, trigger, actor);
        }
    }

    private List<LineAllocation> allocateDay(LocalDate date, List<WheelIntakeRequestEntity> openRequests,
                                             Map<UUID, Map<WheelType, Integer>> unplanned,
                                             ProductionTargetConfigurationEntity target) {
        List<LineAllocation> allocations = new ArrayList<>();
        int planned = 0;
        List<WheelIntakeRequestEntity> eligible = eligibleRequests(date, openRequests, unplanned);

        for (WheelIntakeRequestEntity request : eligible.stream().filter(request -> dueDate(request).isBefore(date) || dueDate(request).isEqual(date)).toList()) {
            int quantity = total(unplanned.get(request.getId()));
            if (quantity > 0) {
                allocations.add(new LineAllocation(request, consume(unplanned.get(request.getId()), quantity), carriedOver(request, date), false));
                planned += quantity;
            }
        }

        int desired = desiredDailyLoad(date, eligible, unplanned, target, planned);
        for (WheelIntakeRequestEntity request : eligible) {
            int remaining = total(unplanned.get(request.getId()));
            if (remaining <= 0 || planned >= desired) {
                continue;
            }
            int quantity = Math.min(remaining, desired - planned);
            allocations.add(new LineAllocation(request, consume(unplanned.get(request.getId()), quantity), carriedOver(request, date), dueDate(request).isAfter(date)));
            planned += quantity;
        }
        return allocations;
    }

    private int desiredDailyLoad(LocalDate date, List<WheelIntakeRequestEntity> eligible, Map<UUID, Map<WheelType, Integer>> unplanned,
                                 ProductionTargetConfigurationEntity target, int alreadyPlanned) {
        int totalEligible = eligible.stream().mapToInt(request -> total(unplanned.get(request.getId()))).sum();
        if (totalEligible <= 0) {
            return alreadyPlanned;
        }
        LocalDate lastDue = eligible.stream()
                .filter(request -> total(unplanned.get(request.getId())) > 0)
                .map(this::dueDate)
                .max(LocalDate::compareTo)
                .orElse(date);
        int days = Math.max(1, (int) java.time.temporal.ChronoUnit.DAYS.between(date, lastDue) + 1);
        int balanced = (int) Math.ceil((double) totalEligible / days);
        int desired = Math.max(target.getMinimumDailyTarget(), balanced);
        desired = Math.min(desired, target.getRegularDailyCapacity());
        return Math.max(alreadyPlanned, desired);
    }

    private List<WheelIntakeRequestEntity> eligibleRequests(LocalDate date, List<WheelIntakeRequestEntity> openRequests,
                                                            Map<UUID, Map<WheelType, Integer>> unplanned) {
        return openRequests.stream()
                .filter(request -> total(unplanned.get(request.getId())) > 0)
                .filter(request -> availabilityDate(request).isBefore(date) || availabilityDate(request).isEqual(date))
                .sorted(requestComparator().thenComparing(WheelIntakeRequestEntity::getId))
                .toList();
    }

    private void savePlan(LocalDate date, List<LineAllocation> allocations, ProductionTargetConfigurationEntity target,
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
        int overtime = Math.max(total - target.getRegularDailyCapacity(), 0);
        int belowMinimum = Math.max(target.getMinimumDailyTarget() - total, 0);
        plan.setCapacityUsed(total);
        plan.setTargetUsed(target.getMinimumDailyTarget());
        plan.setMinimumTargetSnapshot(target.getMinimumDailyTarget());
        plan.setMaximumTargetSnapshot(target.getRegularDailyCapacity());
        plan.setTotalKnownWheels(total);
        plan.setTotalPlanned(total);
        plan.setTotalCompleted(0);
        plan.setTotalRemaining(total);
        plan.setTotalWaitingForArrival(0);
        plan.setTotalFutureWorkload(0);
        plan.setTotalAtRisk(overtime);
        plan.setTotalOverCapacity(overtime);
        plan.setOvertimeQuantity(overtime);
        plan.setBelowMinimumQuantity(belowMinimum);
        plan.setCarriedOverQuantity(allocations.stream().filter(LineAllocation::carriedOver).mapToInt(LineAllocation::quantity).sum());
        plan.setAdvancedQuantity(allocations.stream().filter(LineAllocation::advancedFromFuture).mapToInt(LineAllocation::quantity).sum());
        plan.setFallbackEstimatesUsed(false);
        plan.setRequiresRecalculation(false);
        plan.setWarning(warning(total, target, provisional));
        ProductionPlanEntity saved = plans.saveAndFlush(plan);
        List<ProductionPlanItemEntity> savedItems = planItems.saveAll(allocations.stream()
                .map(new AllocationToEntity(saved))
                .toList());
        capacityAlerts.upsertFromPlan(saved, savedItems);
        auditService.record(saved.getId(), null, "MULTI_DAY_PLAN_GENERATED", actor,
                "Generated daily distribution for " + date + " with " + total + " wheels.");
        dashboardEvents.publishPlanUpdated(date);
    }

    private String warning(int total, ProductionTargetConfigurationEntity target, boolean provisional) {
        List<String> warnings = new ArrayList<>();
        if (provisional) {
            warnings.add("Plano provisório: existe fecho do turno anterior pendente.");
        }
        if (total < target.getMinimumDailyTarget()) {
            warnings.add("Target mínimo não atingido: não existe trabalho elegível suficiente.");
        }
        if (total > target.getRegularDailyCapacity()) {
            warnings.add("Horas extra necessárias: capacidade regular excedida.");
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
                .map(this::dueDate)
                .max(LocalDate::compareTo)
                .orElse(from.plusDays(1));
        LocalDate existing = plans.findMaxCurrentPlanningDate();
        LocalDate horizon = existing == null ? requestHorizon : max(requestHorizon, existing);
        return max(horizon, from.plusDays(1));
    }

    private Comparator<WheelIntakeRequestEntity> requestComparator() {
        return Comparator
                .comparing(this::dueAt)
                .thenComparing(this::availableAt)
                .thenComparing(WheelIntakeRequestEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(WheelIntakeRequestEntity::getId);
    }

    private boolean carriedOver(WheelIntakeRequestEntity request, LocalDate date) {
        return !planItems.findClosedCarryOver(
                request.getId(), date, ProductionPlanStatus.CLOSED
        ).isEmpty();
    }

    private LocalDate availabilityDate(WheelIntakeRequestEntity request) {
        return availableAt(request).atZoneSameInstant(businessZone).toLocalDate();
    }

    private LocalDate dueDate(WheelIntakeRequestEntity request) {
        return dueAt(request).atZoneSameInstant(businessZone).toLocalDate();
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

    private int total(Map<WheelType, Integer> quantities) {
        return quantities == null ? 0 : wheelQuantityService.total(quantities);
    }

    private Map<WheelType, Integer> consume(Map<WheelType, Integer> source, int quantity) {
        Map<WheelType, Integer> consumed = wheelQuantityService.empty();
        int remaining = quantity;
        for (WheelType type : WheelType.values()) {
            int available = source.getOrDefault(type, 0);
            int taken = Math.min(available, remaining);
            consumed.put(type, taken);
            source.put(type, available - taken);
            remaining -= taken;
            if (remaining == 0) {
                break;
            }
        }
        return consumed;
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
                request.getExpectedFactoryDropOffWindowStart(),
                request.getExpectedFactoryDropOffWindowEnd(),
                request.getRequestedFactoryPickupWindowStart(),
                request.getRequestedFactoryPickupWindowEnd(),
                request.getNotes(),
                line.getOperationalNotes(),
                line.isCarriedOver(),
                line.isAdvancedFromFuture(),
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

    private record LineAllocation(WheelIntakeRequestEntity request, Map<WheelType, Integer> quantities,
                                  boolean carriedOver, boolean advancedFromFuture) {
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
            entity.setAvailabilityAt(availableAt(request));
            entity.setRequiredReadyAt(dueAt(request));
            entity.setAssignedProductionDate(plan.getPlanningDate());
            entity.setAssignedWindowLabel("Plano diário");
            entity.setAvailabilityClassification(request.getActualFactoryArrivalAt() == null
                    ? AvailabilityClassification.TENTATIVE : AvailabilityClassification.CONFIRMED);
            entity.setRiskClassification(dueDate(request).isBefore(plan.getPlanningDate())
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
            parts.add("Prazo: " + dueAt(request).atZoneSameInstant(businessZone).toLocalDate() + ".");
            return String.join(" ", parts);
        }
    }
}
