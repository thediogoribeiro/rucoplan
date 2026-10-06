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
import pt.rucodel.productionplanning.mapper.ApiMapper;
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
    private final ProductionPlanItemReconciliationRepository reconciliations;
    private final WheelIntakeRequestRepository requests;
    private final ProductionTargetService targetService;
    private final PlanningRunRepository planningRuns;
    private final AuditService auditService;
    private final DashboardEventPublisher dashboardEvents;
    private final ApiMapper mapper;
    private final Clock clock;
    private final ZoneId businessZone;
    private final WheelQuantityService wheelQuantityService;
    private final ProductionSiteService productionSites;
    private final double saturdayProductionFactor;
    private final double sundayProductionFactor;
    private final LocalTime weekdayProductionStart;
    private final LocalTime weekdayProductionEnd;
    private final LocalTime saturdayProductionStart;
    private final LocalTime saturdayProductionEnd;

    public MultiDayProductionPlanningService(ProductionPlanRepository plans, ProductionPlanItemRepository planItems,
                                             ProductionPlanItemReconciliationRepository reconciliations,
                                             WheelIntakeRequestRepository requests, ProductionTargetService targetService,
                                             PlanningRunRepository planningRuns,
                                             AuditService auditService, DashboardEventPublisher dashboardEvents, ApiMapper mapper,
                                             Clock clock, AppProperties appProperties, WheelQuantityService wheelQuantityService,
                                             ProductionSiteService productionSites,
                                             @Value("${app.planning.saturday-production-factor:0.5}") double saturdayProductionFactor,
                                             @Value("${app.planning.sunday-production-factor:0}") double sundayProductionFactor,
                                             @Value("${app.planning.weekday-production-start:04:00}") String weekdayProductionStart,
                                             @Value("${app.planning.weekday-production-end:22:00}") String weekdayProductionEnd,
                                             @Value("${app.planning.saturday-production-start:04:00}") String saturdayProductionStart,
                                             @Value("${app.planning.saturday-production-end:13:00}") String saturdayProductionEnd) {
        this.plans = plans;
        this.planItems = planItems;
        this.reconciliations = reconciliations;
        this.requests = requests;
        this.targetService = targetService;
        this.planningRuns = planningRuns;
        this.auditService = auditService;
        this.dashboardEvents = dashboardEvents;
        this.mapper = mapper;
        this.clock = clock;
        this.businessZone = ZoneId.of(appProperties.timezone());
        this.wheelQuantityService = wheelQuantityService;
        this.productionSites = productionSites;
        this.saturdayProductionFactor = saturdayProductionFactor;
        this.sundayProductionFactor = sundayProductionFactor;
        this.weekdayProductionStart = LocalTime.parse(weekdayProductionStart);
        this.weekdayProductionEnd = LocalTime.parse(weekdayProductionEnd);
        this.saturdayProductionStart = LocalTime.parse(saturdayProductionStart);
        this.saturdayProductionEnd = LocalTime.parse(saturdayProductionEnd);
    }

    @Transactional(readOnly = true)
    public List<DailyProductionPlanResponse> list(LocalDate from, LocalDate to) {
        return list(ProductionSiteCode.PT, from, to);
    }

    @Transactional(readOnly = true)
    public List<DailyProductionPlanResponse> list(ProductionSiteCode siteCode, LocalDate from, LocalDate to) {
        List<DailyProductionPlanResponse> response = plans.findByProductionSite_CodeAndPlanningDateBetweenAndCurrentPlanTrueOrderByPlanningDateAsc(siteCode, from, to).stream()
                .filter(plan -> isProductionDay(plan.getPlanningDate()) || plan.getStatus() == ProductionPlanStatus.CLOSED)
                .map(plan -> toResponse(plan, planItems.findByPlanIdForSiteOrderByPriorityScoreAsc(siteCode, plan.getId())))
                .toList();
        LOGGER.info("Production plans queried site={} from={} to={} plans={} items={}",
                siteCode, from, to, response.size(), response.stream().mapToInt(plan -> plan.lines().size()).sum());
        return response;
    }

    @Transactional(readOnly = true)
    public DailyProductionPlanResponse getOrGenerate(LocalDate date) {
        return getOrGenerate(ProductionSiteCode.PT, date);
    }

    @Transactional(readOnly = true)
    public DailyProductionPlanResponse getOrGenerate(ProductionSiteCode siteCode, LocalDate date) {
        return getOrGenerate(siteCode, date, null, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public DailyProductionPlanResponse getOrGenerate(ProductionSiteCode siteCode, LocalDate date,
                                                     String driver,
                                                     UUID customerId,
                                                     LifecycleStatus status,
                                                     WheelType wheelType,
                                                     AvailabilityClassification availability,
                                                     RiskClassification risk) {
        if (!isProductionDay(date)) {
            return nonProductionDayResponse(siteCode, date);
        }
        return plans.findFirstByProductionSite_CodeAndPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(siteCode, date)
                .map(plan -> toResponse(plan, filteredPlanLines(
                        planItems.findByPlanIdForSiteOrderByPriorityScoreAsc(siteCode, plan.getId()),
                        driver, customerId, status, wheelType, availability, risk)))
                .orElseGet(() -> emptyProductionDayResponse(siteCode, date));
    }

    @Transactional(readOnly = true)
    public boolean needsInitialPlanning(LocalDate from) {
        return needsInitialPlanning(ProductionSiteCode.PT, from);
    }

    @Transactional(readOnly = true)
    public boolean needsInitialPlanning(ProductionSiteCode siteCode, LocalDate from) {
        return (requests.countByLifecycleStatusForSite(siteCode, LifecycleStatus.COMMUNICATED) > 0
                || requests.countByLifecycleStatusForSite(siteCode, LifecycleStatus.AT_FACTORY) > 0
                || requests.countByLifecycleStatusForSite(siteCode, LifecycleStatus.IN_PRODUCTION) > 0)
                && plans.countByProductionSite_CodeAndCurrentPlanTrueAndStatusNot(siteCode, ProductionPlanStatus.CLOSED) == 0;
    }

    @Transactional
    public DailyProductionPlanResponse recalculate(LocalDate from, GenerationTrigger trigger, AuthenticatedUser user) {
        return recalculate(user.productionSiteCode(), from, trigger, user.actorLabel());
    }

    @Transactional
    public DailyProductionPlanResponse recalculate(LocalDate from, GenerationTrigger trigger, String actor) {
        return recalculate(ProductionSiteCode.PT, from, trigger, actor);
    }

    @Transactional
    public DailyProductionPlanResponse recalculate(ProductionSiteCode siteCode, LocalDate from, GenerationTrigger trigger, String actor) {
        int eligibleRequestCount = eligibleRequestCount(siteCode, from);
        boolean hasPlanningDemand = !planningDemands(siteCode).isEmpty();
        Optional<ProductionPlanEntity> current = plans.findFirstByProductionSite_CodeAndPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(siteCode, from);
        if (trigger == GenerationTrigger.MANUAL && current.filter(plan -> plan.getStatus() == ProductionPlanStatus.CLOSED).isPresent()) {
            ProductionPlanEntity closed = current.get();
            boolean emptyClosedPlan = planItems.findByPlanIdForSiteOrderByPriorityScoreAsc(siteCode, closed.getId()).isEmpty()
                    && closed.getTotalPlanned() == 0;
            if (eligibleRequestCount == 0 && emptyClosedPlan) {
                LOGGER.info("Clearing empty closed production plan with no eligible requests site={} planningDate={} planId={}",
                        siteCode, from, closed.getId());
                plans.clearCurrentPlanForSite(siteCode, from);
                dashboardEvents.publishPlanUpdated(siteCode, from);
                if (!hasPlanningDemand) {
                    return emptyProductionDayResponse(siteCode, from);
                }
                current = Optional.empty();
            }
            if (current.filter(plan -> plan.getStatus() == ProductionPlanStatus.CLOSED).isPresent()) {
                throw new InvalidRequestException("PRODUCTION_PLAN_CLOSED",
                        "O plano de " + from + " já está fechado e não pode ser regenerado.");
            }
        }
        if (trigger == GenerationTrigger.MANUAL && !hasPlanningDemand) {
            plans.clearCurrentPlanForSite(siteCode, from);
            dashboardEvents.publishPlanUpdated(siteCode, from);
            return emptyProductionDayResponse(siteCode, from);
        }
        LocalDate to = planningHorizon(siteCode, from);
        PlanningRunEntity run = startRun(siteCode, from, to, trigger, actor);
        try {
            generateRange(siteCode, from, to, trigger, actor);
            run.setStatus(PlanningRunStatus.COMPLETED);
            run.setFinishedAt(OffsetDateTime.now(clock));
            run.setSummary("Planos recalculados de " + from + " a " + to + ".");
            planningRuns.save(run);
            return isProductionDay(from) ? getExistingOrEmpty(siteCode, from) : nonProductionDayResponse(siteCode, from);
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
        return reconciliation(ProductionSiteCode.PT, date);
    }

    @Transactional(readOnly = true)
    public DailyProductionPlanResponse reconciliation(ProductionSiteCode siteCode, LocalDate date) {
        return getExisting(siteCode, date);
    }

    @Transactional(readOnly = true)
    public DailyProductionPlanResponse openReconciliation(LocalDate date) {
        return openReconciliation(ProductionSiteCode.PT, date);
    }

    @Transactional(readOnly = true)
    public DailyProductionPlanResponse openReconciliation(ProductionSiteCode siteCode, LocalDate date) {
        ProductionPlanEntity plan = currentPlan(siteCode, date);
        return toResponse(plan, planItems.findOpenByPlanIdForSiteOrderByPriorityScoreAsc(siteCode, plan.getId()));
    }

    @Transactional(readOnly = true)
    public DailyProductionPlanResponse closedReconciliation(LocalDate date) {
        return closedReconciliation(ProductionSiteCode.PT, date);
    }

    @Transactional(readOnly = true)
    public DailyProductionPlanResponse closedReconciliation(ProductionSiteCode siteCode, LocalDate date) {
        ProductionPlanEntity plan = currentPlan(siteCode, date);
        return toResponse(plan, planItems.findByPlanIdForSiteOrderByPriorityScoreAsc(siteCode, plan.getId()).stream()
                .filter(this::isClosedLine)
                .toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestResponse> requestsForProductionDate(LocalDate productionDate, int page, int size) {
        return requestsForProductionDate(ProductionSiteCode.PT, productionDate, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestResponse> requestsForProductionDate(ProductionSiteCode siteCode, LocalDate productionDate, int page, int size) {
        List<RequestResponse> all = planItems.findCurrentByProductionDateForSite(siteCode, productionDate).stream()
                .map(item -> mapper.toRequest(item.getRequest(), item))
                .toList();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        int totalPages = all.isEmpty() ? 0 : (int) Math.ceil((double) all.size() / safeSize);
        return new PageResponse<>(all.subList(from, to), safePage, safeSize, all.size(), totalPages);
    }

    @Transactional
    public DailyProductionPlanResponse saveReconciliation(LocalDate date, ReconciliationRequest request, AuthenticatedUser user) {
        ProductionPlanEntity plan = currentPlan(user.productionSiteCode(), date);
        requireNotClosed(plan);
        requireVersion(plan, request.planVersion());
        applyReconciliation(plan, request);
        plan.setStatus(ProductionPlanStatus.AWAITING_RECONCILIATION);
        ProductionPlanEntity saved = plans.saveAndFlush(plan);
        auditService.record(saved.getId(), null, "SHIFT_RECONCILIATION_SAVED", user.actorLabel(),
                "Reconciliation draft saved for " + date + ".");
        return toResponse(saved, planItems.findByPlanIdForSiteOrderByPriorityScoreAsc(user.productionSiteCode(), saved.getId()));
    }

    @Transactional
    public DailyProductionPlanResponse close(LocalDate date, ReconciliationRequest request, AuthenticatedUser user) {
        ProductionPlanEntity plan = currentPlan(user.productionSiteCode(), date);
        if (plan.getStatus() == ProductionPlanStatus.CLOSED) {
            return toResponse(plan, planItems.findByPlanIdForSiteOrderByPriorityScoreAsc(user.productionSiteCode(), plan.getId()));
        }
        requireVersion(plan, request.planVersion());
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new InvalidRequestException("É obrigatório validar todas as linhas do plano.");
        }
        Map<UUID, ReconciliationLineRequest> byLine = request.lines().stream()
                .collect(Collectors.toMap(ReconciliationLineRequest::lineId, Function.identity()));
        List<ProductionPlanItemEntity> lines = planItems.findByPlanIdForSiteOrderByPriorityScoreAsc(user.productionSiteCode(), plan.getId());
        if (byLine.size() != lines.size()) {
            throw new InvalidRequestException("É obrigatório validar todas as linhas do plano.");
        }
        for (ProductionPlanItemEntity line : lines) {
            ReconciliationLineRequest update = byLine.get(line.getId());
            if (update == null) {
                throw new InvalidRequestException("É obrigatório validar todas as linhas do plano.");
            }
            closePlanItemInternal(date, line.getId(), update.version(), completedByType(update, line), update.operationalNotes(), user, false);
        }
        plan = currentPlan(user.productionSiteCode(), date);
        updatePlanTotalsAndStatus(plan, user.actorLabel());
        ProductionPlanEntity saved = plans.saveAndFlush(plan);
        generateRange(user.productionSiteCode(), date.plusDays(1), planningHorizon(user.productionSiteCode(), date.plusDays(1)), GenerationTrigger.AUTOMATIC_RECALCULATION, "SYSTEM");
        dashboardEvents.publishPlanUpdated(user.productionSiteCode(), date);
        return toResponse(saved, planItems.findByPlanIdForSiteOrderByPriorityScoreAsc(user.productionSiteCode(), saved.getId()));
    }

    @Transactional
    public DailyProductionPlanResponse closeItem(LocalDate date, UUID itemId, PlanItemCloseRequest request, AuthenticatedUser user) {
        closePlanItemInternal(date, itemId, request.version(), completedByType(request), request.operationalNotes(), user, true);
        return getExisting(user.productionSiteCode(), date);
    }

    @Transactional
    public DailyProductionPlanResponse reopen(LocalDate date, ReopenPlanRequest request, AuthenticatedUser user) {
        ProductionPlanEntity plan = currentPlan(user.productionSiteCode(), date);
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
        generateRange(user.productionSiteCode(), date.plusDays(1), planningHorizon(user.productionSiteCode(), date.plusDays(1)), GenerationTrigger.AUTOMATIC_RECALCULATION, "SYSTEM");
        return toResponse(plans.saveAndFlush(plan), planItems.findByPlanIdForSiteOrderByPriorityScoreAsc(user.productionSiteCode(), plan.getId()));
    }

    @Transactional
    public DailyProductionPlanResponse reopenItem(LocalDate date, UUID itemId, PlanItemReopenRequest request, AuthenticatedUser user) {
        if (user.role() != UserRole.ADMIN) {
            throw new InvalidRequestException("FORBIDDEN_OPERATION", "Apenas administradores podem reabrir fechos.");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new InvalidRequestException("É obrigatório indicar o motivo da reabertura.");
        }
        ProductionPlanItemEntity line = requirePlanItem(user.productionSiteCode(), date, itemId);
        if (line.getVersion() != request.version()) {
            throw new InvalidRequestException("OPTIMISTIC_LOCK", "A linha do plano foi alterada por outro utilizador.");
        }
        if (line.getLineStatus() == ProductionPlanLineStatus.REOPENED || line.getLineStatus() == ProductionPlanLineStatus.OPEN) {
            return getExisting(user.productionSiteCode(), date);
        }
        ProductionPlanItemReconciliationEntity active = reconciliations
                .findFirstByProductionSite_CodeAndProductionPlanItemIdAndRevertedFalseOrderByRevisionDesc(user.productionSiteCode(), itemId)
                .orElseThrow(() -> new InvalidRequestException("PLAN_ITEM_RECONCILIATION_NOT_FOUND",
                        "Não existe fecho ativo para reabrir nesta linha."));
        if (!reconciliations.findActiveLaterReconciliationsForSite(user.productionSiteCode(), line.getRequest().getId(), date).isEmpty()) {
            throw new InvalidRequestException("PLAN_ITEM_REOPEN_BLOCKED_BY_LATER_CLOSURE",
                    "Este fecho possui trabalho associado já concluído num dia posterior. Reabra primeiro os fechos posteriores relacionados.");
        }

        WheelIntakeRequestEntity intake = line.getRequest();
        intake.subtractCompletedWheelQuantities(reconciliationCompletedByType(active));
        refreshRequestStatusAfterReconciliation(intake, 0, line.getPlan().getId(), user, "PLAN_ITEM_REOPENED");
        requests.save(intake);

        active.setReverted(true);
        active.setStatus(ProductionPlanItemReconciliationStatus.REOPENED);
        active.setReopenedAt(OffsetDateTime.now(clock));
        active.setReopenedBy(user.actorLabel());
        active.setReopenReason(request.reason().trim());
        active.setUpdatedBy(user.actorLabel());
        reconciliations.save(active);

        line.applyWheelQuantityReconciliation(wheelQuantityService.empty(), line.plannedWheelQuantityMap());
        line.setLineStatus(ProductionPlanLineStatus.REOPENED);
        line.setClosedAt(null);
        line.setClosedBy(null);
        line.setReopenedAt(OffsetDateTime.now(clock));
        line.setReopenedBy(user.actorLabel());
        line.setOperationalNotes(null);
        planItems.save(line);

        ProductionPlanEntity plan = line.getPlan();
        plan.setStatus(ProductionPlanStatus.AWAITING_RECONCILIATION);
        plan.setClosedAt(null);
        plan.setClosedBy(null);
        updatePlanTotalsAndStatus(plan, user.actorLabel());
        plans.saveAndFlush(plan);
        auditService.record(plan.getId(), intake.getId(), "PLAN_ITEM_REOPENED", user.actorLabel(), request.reason().trim());
        auditService.record(plan.getId(), intake.getId(), "CARRY_OVER_REVERSED", user.actorLabel(),
                "Reopened plan item " + line.getId() + ".");
        generateRange(user.productionSiteCode(), date.plusDays(1), planningHorizon(user.productionSiteCode(), date.plusDays(1)), GenerationTrigger.AUTOMATIC_RECALCULATION, "SYSTEM");
        dashboardEvents.publishPlanUpdated(user.productionSiteCode(), date);
        return getExisting(user.productionSiteCode(), date);
    }

    private void closePlanItemInternal(LocalDate date, UUID itemId, Long version, Map<WheelType, Integer> completedByType,
                                       String operationalNotes, AuthenticatedUser user, boolean recalculateFuture) {
        ProductionPlanItemEntity line = requirePlanItem(user.productionSiteCode(), date, itemId);
        if (isClosedLine(line)) {
            return;
        }
        if (line.getPlan().getStatus() == ProductionPlanStatus.CLOSED) {
            throw new InvalidRequestException("PRODUCTION_PLAN_CLOSED",
                    "O plano de " + date + " já está fechado e não pode ser alterado.");
        }
        if (version == null || line.getVersion() != version) {
            throw new InvalidRequestException("OPTIMISTIC_LOCK", "A linha do plano foi alterada por outro utilizador.");
        }
        Map<WheelType, Integer> remainingByType = validateAndRemainingByType(line, completedByType);
        int completedTotal = total(completedByType);
        int remainingTotal = total(remainingByType);
        if (completedTotal <= 0) {
            throw new InvalidRequestException("PLAN_ITEM_NO_COMPLETED_QUANTITY",
                    "Indique pelo menos uma jante concluída antes de fechar o trabalho no turno.");
        }
        if (line.getRequest().getLifecycleStatus() == LifecycleStatus.COMMUNICATED && completedTotal > 0) {
            throw new InvalidRequestException("REQUEST_NOT_AT_FACTORY",
                    "Pedido comunicado não pode ser concluído no fecho sem confirmação de chegada à fábrica.");
        }

        line.applyWheelQuantityReconciliation(completedByType, remainingByType);
        line.setOperationalNotes(blankToNull(operationalNotes));
        line.setLineStatus(remainingTotal == 0
                ? ProductionPlanLineStatus.CLOSED_COMPLETE
                : ProductionPlanLineStatus.CLOSED_PARTIAL);
        line.setClosedAt(OffsetDateTime.now(clock));
        line.setClosedBy(user.actorLabel());
        line.setReopenedAt(null);
        line.setReopenedBy(null);
        planItems.save(line);

        WheelIntakeRequestEntity intake = line.getRequest();
        intake.addCompletedWheelQuantities(completedByType);
        refreshRequestStatusAfterReconciliation(intake, completedTotal, line.getPlan().getId(), user,
                remainingTotal == 0 ? "PLAN_ITEM_CLOSED_COMPLETE" : "PLAN_ITEM_CLOSED_PARTIAL");
        requests.save(intake);

        ProductionPlanItemReconciliationEntity reconciliation = new ProductionPlanItemReconciliationEntity();
        reconciliation.setProductionSite(line.getProductionSite());
        reconciliation.setProductionPlan(line.getPlan());
        reconciliation.setProductionPlanItem(line);
        reconciliation.setRequest(intake);
        reconciliation.setPlanningDate(date);
        reconciliation.setStatus(remainingTotal == 0
                ? ProductionPlanItemReconciliationStatus.CLOSED_COMPLETE
                : ProductionPlanItemReconciliationStatus.CLOSED_PARTIAL);
        reconciliation.setRevision(reconciliations.findMaxRevisionForSite(user.productionSiteCode(), line.getId()) + 1);
        reconciliation.setClosedAt(line.getClosedAt());
        reconciliation.setClosedBy(user.actorLabel());
        reconciliation.setCreatedBy(user.actorLabel());
        reconciliation.setUpdatedBy(user.actorLabel());
        for (ProductionPlanItemWheelQuantityEntity quantity : line.getWheelQuantities()) {
            ProductionPlanItemReconciliationQuantityEntity item = new ProductionPlanItemReconciliationQuantityEntity();
            item.setReconciliation(reconciliation);
            item.setWheelType(quantity.getWheelType());
            item.setPlannedQuantity(quantity.getPlannedQuantity());
            item.setCompletedQuantity(quantity.getCompletedQuantity());
            item.setRemainingQuantity(quantity.getRemainingQuantity());
            item.setCreatedBy(user.actorLabel());
            item.setUpdatedBy(user.actorLabel());
            reconciliation.getQuantities().add(item);
        }
        reconciliations.save(reconciliation);

        ProductionPlanEntity plan = line.getPlan();
        updatePlanTotalsAndStatus(plan, user.actorLabel());
        plans.saveAndFlush(plan);
        auditService.record(plan.getId(), intake.getId(), line.getLineStatus() == ProductionPlanLineStatus.CLOSED_COMPLETE
                        ? "PLAN_ITEM_CLOSED_COMPLETE"
                        : "PLAN_ITEM_CLOSED_PARTIAL",
                user.actorLabel(),
                "Closed plan item " + line.getId() + ". Completed " + completedTotal + ", pending " + remainingTotal + ".");
        if (remainingTotal > 0) {
            auditService.record(plan.getId(), intake.getId(), "CARRY_OVER_CREATED", user.actorLabel(),
                    "Pending " + remainingTotal + " wheels carried to the next eligible production day.");
        }
        if (recalculateFuture) {
            generateRange(user.productionSiteCode(), date.plusDays(1), planningHorizon(user.productionSiteCode(), date.plusDays(1)), GenerationTrigger.AUTOMATIC_RECALCULATION, "SYSTEM");
            dashboardEvents.publishPlanUpdated(user.productionSiteCode(), date);
        }
    }

    private ProductionPlanItemEntity requirePlanItem(LocalDate date, UUID itemId) {
        return requirePlanItem(ProductionSiteCode.PT, date, itemId);
    }

    private ProductionPlanItemEntity requirePlanItem(ProductionSiteCode siteCode, LocalDate date, UUID itemId) {
        ProductionPlanItemEntity line = planItems.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Production plan item was not found."));
        if (!line.getPlan().getPlanningDate().equals(date)
                || !line.getPlan().isCurrentPlan()
                || line.getProductionSite() == null
                || line.getProductionSite().getCode() != siteCode) {
            throw new EntityNotFoundException("Production plan item was not found for the selected date.");
        }
        return line;
    }

    private Map<WheelType, Integer> completedByType(PlanItemCloseRequest request) {
        if (request.wheelQuantities() == null || request.wheelQuantities().isEmpty()) {
            throw new InvalidRequestException("É obrigatório indicar as quantidades concluídas por tipo.");
        }
        Map<WheelType, Integer> completedByType = wheelQuantityService.empty();
        Set<WheelType> seen = new HashSet<>();
        for (PlanItemCloseWheelQuantityRequest quantity : request.wheelQuantities()) {
            if (quantity == null || quantity.type() == null || quantity.completedQuantity() == null) {
                throw new InvalidRequestException("É obrigatório indicar tipo e quantidade concluída.");
            }
            if (!seen.add(quantity.type())) {
                throw new InvalidRequestException("Não é permitido repetir tipos de jantes no fecho.");
            }
            if (quantity.completedQuantity() < 0) {
                throw new InvalidRequestException("As quantidades do fecho não podem ser negativas.");
            }
            completedByType.put(quantity.type(), quantity.completedQuantity());
        }
        return completedByType;
    }

    private Map<WheelType, Integer> completedByType(ReconciliationLineRequest update, ProductionPlanItemEntity line) {
        if (update.wheelQuantities() == null || update.wheelQuantities().isEmpty()) {
            if (update.completedQuantity() == null) {
                throw new InvalidRequestException("É obrigatório indicar as quantidades concluídas.");
            }
            if (update.remainingQuantity() != null && update.completedQuantity() + update.remainingQuantity() != line.getQuantity()) {
                throw new InvalidRequestException("Fecho inconsistente: planeado tem de ser igual a concluído mais pendente.");
            }
            return splitAggregateCompletedByPlannedTypes(line, update.completedQuantity());
        }
        Map<WheelType, Integer> completedByType = wheelQuantityService.empty();
        Set<WheelType> seen = new HashSet<>();
        for (ReconciliationLineWheelQuantityRequest quantity : update.wheelQuantities()) {
            if (quantity == null || quantity.type() == null || quantity.completedQuantity() == null) {
                throw new InvalidRequestException("É obrigatório indicar tipo e concluído em cada quantidade.");
            }
            if (!seen.add(quantity.type())) {
                throw new InvalidRequestException("Não é permitido repetir tipos de jantes no fecho.");
            }
            if (quantity.remainingQuantity() != null) {
                int plannedForType = line.getWheelQuantities().stream()
                        .filter(existing -> existing.getWheelType() == quantity.type())
                        .mapToInt(ProductionPlanItemWheelQuantityEntity::getPlannedQuantity)
                        .findFirst()
                        .orElse(0);
                if (quantity.completedQuantity() + quantity.remainingQuantity() != plannedForType) {
                    throw new InvalidRequestException("Fecho inconsistente: cada tipo tem de validar planeado = concluído mais pendente.");
                }
            }
            completedByType.put(quantity.type(), quantity.completedQuantity());
        }
        return completedByType;
    }

    private Map<WheelType, Integer> validateAndRemainingByType(ProductionPlanItemEntity line, Map<WheelType, Integer> completedByType) {
        Map<WheelType, Integer> remainingByType = wheelQuantityService.empty();
        for (ProductionPlanItemWheelQuantityEntity quantity : line.getWheelQuantities()) {
            WheelType type = quantity.getWheelType();
            int completed = completedByType.getOrDefault(type, 0);
            if (completed < 0 || completed > quantity.getPlannedQuantity()) {
                throw new InvalidRequestException("As quantidades não podem ser negativas nem superiores ao planeado.");
            }
            remainingByType.put(type, quantity.getPlannedQuantity() - completed);
        }
        if (total(completedByType) + total(remainingByType) != line.getQuantity()) {
            throw new InvalidRequestException("Fecho inconsistente: planeado tem de ser igual a concluído mais pendente.");
        }
        return remainingByType;
    }

    private Map<WheelType, Integer> reconciliationCompletedByType(ProductionPlanItemReconciliationEntity reconciliation) {
        Map<WheelType, Integer> result = wheelQuantityService.empty();
        for (ProductionPlanItemReconciliationQuantityEntity quantity : reconciliation.getQuantities()) {
            result.put(quantity.getWheelType(), quantity.getCompletedQuantity());
        }
        return result;
    }

    private boolean isClosedLine(ProductionPlanItemEntity line) {
        return line.getLineStatus() == ProductionPlanLineStatus.CLOSED_COMPLETE
                || line.getLineStatus() == ProductionPlanLineStatus.CLOSED_PARTIAL
                || line.getLineStatus() == ProductionPlanLineStatus.COMPLETED
                || line.getLineStatus() == ProductionPlanLineStatus.PARTIALLY_COMPLETED
                || line.getLineStatus() == ProductionPlanLineStatus.CARRIED_OVER;
    }

    private void updatePlanTotalsAndStatus(ProductionPlanEntity plan, String actor) {
        List<ProductionPlanItemEntity> lines = planItems.findByPlanIdForSiteOrderByPriorityScoreAsc(plan.getProductionSite().getCode(), plan.getId());
        plan.setTotalCompleted(lines.stream().mapToInt(ProductionPlanItemEntity::getCompletedQuantity).sum());
        plan.setTotalRemaining(lines.stream().mapToInt(ProductionPlanItemEntity::getRemainingQuantity).sum());
        boolean allClosed = !lines.isEmpty() && lines.stream().allMatch(this::isClosedLine);
        if (allClosed) {
            boolean wasClosed = plan.getStatus() == ProductionPlanStatus.CLOSED;
            plan.setStatus(ProductionPlanStatus.CLOSED);
            if (plan.getClosedAt() == null) {
                plan.setClosedAt(OffsetDateTime.now(clock));
            }
            if (plan.getClosedBy() == null) {
                plan.setClosedBy(actor);
            }
            if (!wasClosed) {
                auditService.record(plan.getId(), null, "SHIFT_CLOSED", actor,
                        "Shift closed. Completed " + plan.getTotalCompleted() + ", remaining " + plan.getTotalRemaining() + ".");
            }
        } else if (lines.stream().anyMatch(this::isClosedLine)) {
            plan.setStatus(ProductionPlanStatus.AWAITING_RECONCILIATION);
        }
    }

    private void refreshRequestStatusAfterReconciliation(WheelIntakeRequestEntity intake, int completedThisClosure,
                                                         UUID planId, AuthenticatedUser user, String eventType) {
        if (intake.getLifecycleStatus() == LifecycleStatus.CANCELLED) {
            return;
        }
        LifecycleStatus previous = intake.getLifecycleStatus();
        LifecycleStatus next;
        if (remainingQuantity(intake) == 0) {
            next = LifecycleStatus.READY_FOR_PICKUP;
        } else if (intake.getCompletedWheelQuantity() > 0 || completedThisClosure > 0) {
            next = LifecycleStatus.IN_PRODUCTION;
        } else if (intake.getActualFactoryArrivalAt() != null) {
            next = LifecycleStatus.AT_FACTORY;
        } else {
            next = LifecycleStatus.COMMUNICATED;
        }
        intake.setLifecycleStatus(next);
        intake.setUpdatedBy(user.actorLabel());
        if (previous != next) {
            auditService.record(planId, intake.getId(), eventType, user.actorLabel(),
                    "Request status changed from " + previous + " to " + next + ".");
        }
        if (next == LifecycleStatus.READY_FOR_PICKUP) {
            auditService.record(planId, intake.getId(), "REQUEST_MARKED_READY", user.actorLabel(),
                    "Pedido concluído no fecho do turno.");
        } else if (previous == LifecycleStatus.READY_FOR_PICKUP && next == LifecycleStatus.IN_PRODUCTION) {
            auditService.record(planId, intake.getId(), "REQUEST_RETURNED_TO_IN_PRODUCTION", user.actorLabel(),
                    "Pedido voltou a produção depois da reabertura de fecho.");
        }
    }

    private void generateRange(LocalDate from, LocalDate to, GenerationTrigger trigger, String actor) {
        generateRange(ProductionSiteCode.PT, from, to, trigger, actor);
    }

    private void generateRange(ProductionSiteCode siteCode, LocalDate from, LocalDate to, GenerationTrigger trigger, String actor) {
        List<PlanningDemand> demands = planningDemands(siteCode);

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (plans.findFirstByProductionSite_CodeAndPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(siteCode, date)
                    .filter(plan -> plan.getStatus() == ProductionPlanStatus.CLOSED)
                    .isPresent()) {
                continue;
            }
            if (!isProductionDay(date)) {
                plans.clearCurrentPlanForSite(siteCode, date);
                auditService.record(null, null, "NON_PRODUCTION_DAY_SKIPPED", actor,
                        "Skipped non-production day " + date + ".");
                dashboardEvents.publishPlanUpdated(siteCode, date);
                continue;
            }
            ProductionTargetConfigurationEntity target = targetService.effectiveFor(siteCode, date);
            EffectiveDailyTargets effectiveTargets = targetsFor(date, target);
            boolean provisional = previousDayOpen(siteCode, date);
            List<LineAllocation> allocations = allocateDay(date, demands, effectiveTargets);
            if (allocations.isEmpty()) {
                plans.clearCurrentPlanForSite(siteCode, date);
                auditService.record(null, null, "NO_PRODUCTION_PLANNED", actor,
                        "No eligible requests for production on " + date + ".");
                LOGGER.info("No eligible production requests site={} planningDate={} trigger={}", siteCode, date, trigger);
                dashboardEvents.publishPlanUpdated(siteCode, date);
                continue;
            }
            provisional = provisional || allocations.stream().anyMatch(allocation -> allocation.request().getLifecycleStatus() == LifecycleStatus.COMMUNICATED);
            savePlan(siteCode, date, allocations, effectiveTargets, provisional, trigger, actor);
        }
    }

    private List<WheelIntakeRequestEntity> openPlanningRequests(ProductionSiteCode siteCode) {
        return requests.findOpenRequestsForPlanningForSite(siteCode, CLOSED_REQUEST_STATUSES).stream()
                .filter(request -> remainingQuantity(request) > 0)
                .filter(request -> request.getExpectedFactoryDropOffWindowEnd() != null && request.getRequestedFactoryPickupWindowStart() != null)
                .sorted(requestComparator())
                .toList();
    }

    private List<PlanningDemand> planningDemands(ProductionSiteCode siteCode) {
        return openPlanningRequests(siteCode).stream()
                .flatMap(request -> demandsFor(request).stream())
                .sorted(demandComparator())
                .toList();
    }

    private int eligibleRequestCount(ProductionSiteCode siteCode, LocalDate date) {
        return (int) eligibleDemands(date, planningDemands(siteCode)).stream()
                .map(demand -> demand.request.getId())
                .distinct()
                .count();
    }

    private List<LineAllocation> allocateDay(LocalDate date, List<PlanningDemand> demands, EffectiveDailyTargets target) {
        Map<LineKey, MutableLineAllocation> allocations = new LinkedHashMap<>();
        int planned = 0;
        List<PlanningDemand> eligible = eligibleDemands(date, demands);

        for (PlanningDemand demand : eligible.stream().filter(demand -> effectiveDueDate(demand).isBefore(date)
                || effectiveDueDate(demand).isEqual(date)).toList()) {
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
                effectiveDueDate(demand).isAfter(date));
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
                .filter(demand -> availableForProductionOn(date, demand))
                .sorted(demandComparator())
                .toList();
    }

    private boolean communicatedArrivalMissed(PlanningDemand demand, LocalDate date) {
        if (demand.request.getLifecycleStatus() != LifecycleStatus.COMMUNICATED) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        ZoneId zone = zoneFor(demand.request);
        LocalDate today = now.atZoneSameInstant(zone).toLocalDate();
        return !date.isAfter(today) && demand.request.getExpectedFactoryDropOffWindowEnd().isBefore(now);
    }

    private void savePlan(LocalDate date, List<LineAllocation> allocations, EffectiveDailyTargets target,
                          boolean provisional, GenerationTrigger trigger, String actor) {
        savePlan(ProductionSiteCode.PT, date, allocations, target, provisional, trigger, actor);
    }

    private void savePlan(ProductionSiteCode siteCode, LocalDate date, List<LineAllocation> allocations, EffectiveDailyTargets target,
                          boolean provisional, GenerationTrigger trigger, String actor) {
        ProductionSiteEntity site = productionSites.requireByCode(siteCode);
        plans.clearCurrentPlanForSite(siteCode, date);
        ProductionPlanEntity plan = new ProductionPlanEntity();
        plan.setProductionSite(site);
        plan.setPlanningDate(date);
        plan.setVersionNumber(plans.findMaxVersionForSite(siteCode, date) + 1);
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
        dashboardEvents.publishPlanUpdated(siteCode, date);
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
        if (allocations.stream().anyMatch(allocation -> firstProductionDateForAvailability(allocation.request(), allocation.planningAvailableAt())
                .isAfter(effectiveDueDate(allocation.request(), allocation.deadlineAt())))) {
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
        return getExisting(ProductionSiteCode.PT, date);
    }

    private DailyProductionPlanResponse getExisting(ProductionSiteCode siteCode, LocalDate date) {
        ProductionPlanEntity plan = currentPlan(siteCode, date);
        return toResponse(plan, planItems.findByPlanIdForSiteOrderByPriorityScoreAsc(siteCode, plan.getId()));
    }

    private DailyProductionPlanResponse getExistingOrEmpty(ProductionSiteCode siteCode, LocalDate date) {
        return plans.findFirstByProductionSite_CodeAndPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(siteCode, date)
                .map(plan -> toResponse(plan, planItems.findByPlanIdForSiteOrderByPriorityScoreAsc(siteCode, plan.getId())))
                .orElseGet(() -> emptyProductionDayResponse(siteCode, date));
    }

    private ProductionPlanEntity currentPlan(LocalDate date) {
        return currentPlan(ProductionSiteCode.PT, date);
    }

    private ProductionPlanEntity currentPlan(ProductionSiteCode siteCode, LocalDate date) {
        return plans.findFirstByProductionSite_CodeAndPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(siteCode, date)
                .orElseThrow(() -> new EntityNotFoundException("Production plan was not found."));
    }

    private void applyReconciliation(ProductionPlanEntity plan, ReconciliationRequest request) {
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new InvalidRequestException("É obrigatório validar todas as linhas do plano.");
        }
        Map<UUID, ReconciliationLineRequest> byLine = request.lines().stream()
                .collect(Collectors.toMap(ReconciliationLineRequest::lineId, Function.identity()));
        List<ProductionPlanItemEntity> lines = planItems.findByPlanIdForSiteOrderByPriorityScoreAsc(plan.getProductionSite().getCode(), plan.getId());
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
            line.setLineStatus(ProductionPlanLineStatus.OPEN);
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
        return previousDayOpen(ProductionSiteCode.PT, date);
    }

    private boolean previousDayOpen(ProductionSiteCode siteCode, LocalDate date) {
        return plans.findFirstByProductionSite_CodeAndPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(siteCode, date.minusDays(1))
                .map(plan -> plan.getStatus() != ProductionPlanStatus.CLOSED)
                .orElse(false);
    }

    private LocalDate planningHorizon(LocalDate from) {
        return planningHorizon(ProductionSiteCode.PT, from);
    }

    private LocalDate planningHorizon(ProductionSiteCode siteCode, LocalDate from) {
        LocalDate requestHorizon = requests.findOpenRequestsForPlanningForSite(siteCode, CLOSED_REQUEST_STATUSES).stream()
                .filter(request -> remainingQuantity(request) > 0)
                .flatMap(request -> demandsFor(request).stream())
                .map(demand -> max(effectiveDueDate(demand), firstProductionDateForAvailability(demand.request, demand.planningAvailableAt)))
                .max(LocalDate::compareTo)
                .orElse(from.plusDays(1));
        LocalDate existing = plans.findMaxCurrentPlanningDateForSite(siteCode);
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
                .comparing((PlanningDemand demand) -> carriedOver(demand.request, demand.planningAvailableAt.atZoneSameInstant(zoneFor(demand.request)).toLocalDate()) ? 0 : 1)
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
        ProductionSiteCode siteCode = request.getProductionSite() == null ? ProductionSiteCode.PT : request.getProductionSite().getCode();
        return !planItems.findClosedCarryOverForSite(
                siteCode,
                request.getId(), date, ProductionPlanStatus.CLOSED
        ).isEmpty();
    }

    private LocalDate effectiveDueDate(WheelIntakeRequestEntity request) {
        return effectiveDueDate(request, latestEffectiveDeadlineAt(request));
    }

    private LocalDate effectiveDueDate(OffsetDateTime due) {
        return effectiveDueDate(null, due);
    }

    private LocalDate effectiveDueDate(PlanningDemand demand) {
        return effectiveDueDate(demand.request, demand.deadlineAt);
    }

    private LocalDate effectiveDueDate(WheelIntakeRequestEntity request, OffsetDateTime due) {
        ZoneId zone = zoneFor(request);
        LocalDate date = due.atZoneSameInstant(zone).toLocalDate();
        LocalTime time = due.atZoneSameInstant(zone).toLocalTime();
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
        return availableForProductionOn(date, request, availableAt(request));
    }

    private boolean availableForProductionOn(LocalDate date, OffsetDateTime available) {
        return availableForProductionOn(date, null, available);
    }

    private boolean availableForProductionOn(LocalDate date, PlanningDemand demand) {
        return availableForProductionOn(date, demand.request, demand.planningAvailableAt);
    }

    private boolean availableForProductionOn(LocalDate date, WheelIntakeRequestEntity request, OffsetDateTime available) {
        if (!isProductionDay(date)) {
            return false;
        }
        ZoneId zone = zoneFor(request);
        LocalDate availableDate = available.atZoneSameInstant(zone).toLocalDate();
        if (availableDate.isBefore(date)) {
            return true;
        }
        if (availableDate.isAfter(date)) {
            return false;
        }
        return productionWindow(date)
                .map(window -> available.atZoneSameInstant(zone).toLocalTime().isBefore(window.end()))
                .orElse(false);
    }

    private boolean impossibleDeadline(WheelIntakeRequestEntity request) {
        return demandsFor(request).stream().anyMatch(this::impossibleDeadline);
    }

    private boolean impossibleDeadline(PlanningDemand demand) {
        return firstProductionDateForAvailability(demand.request, demand.planningAvailableAt).isAfter(effectiveDueDate(demand));
    }

    private LocalDate firstProductionDateForAvailability(WheelIntakeRequestEntity request) {
        return firstProductionDateForAvailability(request, availableAt(request));
    }

    private LocalDate firstProductionDateForAvailability(OffsetDateTime available) {
        return firstProductionDateForAvailability(null, available);
    }

    private LocalDate firstProductionDateForAvailability(WheelIntakeRequestEntity request, OffsetDateTime available) {
        ZoneId zone = zoneFor(request);
        LocalDate date = available.atZoneSameInstant(zone).toLocalDate();
        LocalTime time = available.atZoneSameInstant(zone).toLocalTime();
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
        return nonProductionDayResponse(ProductionSiteCode.PT, date);
    }

    private DailyProductionPlanResponse nonProductionDayResponse(ProductionSiteCode siteCode, LocalDate date) {
        return plans.findFirstByProductionSite_CodeAndPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(siteCode, date)
                .filter(plan -> plan.getStatus() == ProductionPlanStatus.CLOSED)
                .map(plan -> toResponse(plan, planItems.findByPlanIdForSiteOrderByPriorityScoreAsc(siteCode, plan.getId())))
                .orElseGet(() -> {
                    return new DailyProductionPlanResponse(
                            UUID.nameUUIDFromBytes(("non-production:" + siteCode + ":" + date).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
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
                            0,
                            0,
                            "NON_PRODUCTION_DAY",
                            0,
                            0,
                            List.of()
                    );
                });
    }

    private DailyProductionPlanResponse emptyProductionDayResponse(LocalDate date) {
        return emptyProductionDayResponse(ProductionSiteCode.PT, date);
    }

    private DailyProductionPlanResponse emptyProductionDayResponse(ProductionSiteCode siteCode, LocalDate date) {
        EffectiveDailyTargets target = targetsFor(date, targetService.effectiveFor(siteCode, date));
        return new DailyProductionPlanResponse(
                UUID.nameUUIDFromBytes(("empty-plan:" + siteCode + ":" + date).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
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
                "Não existem pedidos elegíveis para produção nesta data. Não há produção planeada para este dia.",
                OffsetDateTime.now(clock),
                null,
                null,
                GenerationTrigger.AUTOMATIC_RECALCULATION,
                0,
                0,
                0,
                "NO_PRODUCTION",
                0,
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
            if (request.getProductionSite() != null
                    && request.getProductionSite().getCode() == ProductionSiteCode.LUX
                    && type == WheelType.BIPARTITE) {
                continue;
            }
            int quantity = remainingByType.getOrDefault(type, 0);
            if (quantity <= 0) {
                continue;
            }
            OffsetDateTime materialAvailableAt = availableAt(request);
            OffsetDateTime deadlineAt = effectiveDeadlineAt(request, type);
            String adjustmentReason = deadlineAdjustmentReason(request, type, deadlineAt);
            OffsetDateTime planningAvailableAt = planningAvailableAt(request, type, materialAvailableAt, deadlineAt);
            result.add(new PlanningDemand(request, type, materialAvailableAt, planningAvailableAt,
                    deadlineAt, adjustmentReason, quantity));
        }
        return result;
    }

    private OffsetDateTime planningAvailableAt(WheelIntakeRequestEntity request, WheelType type,
                                               OffsetDateTime materialAvailableAt, OffsetDateTime deadlineAt) {
        if (type != WheelType.BIPARTITE) {
            return materialAvailableAt;
        }
        ZoneId zone = zoneFor(request);
        LocalDate date = deadlineAt.atZoneSameInstant(zone).toLocalDate();
        LocalTime start = productionWindow(date).map(ProductionWindow::start).orElse(LocalTime.MIN);
        OffsetDateTime earliestBipartiteCompletionDay = date.atTime(start).atZone(zone).toOffsetDateTime();
        return earliestBipartiteCompletionDay.isAfter(materialAvailableAt) ? earliestBipartiteCompletionDay : materialAvailableAt;
    }

    private OffsetDateTime latestEffectiveDeadlineAt(WheelIntakeRequestEntity request) {
        return demandsFor(request).stream()
                .map(demand -> demand.deadlineAt)
                .max(OffsetDateTime::compareTo)
                .orElseGet(() -> dueAt(request));
    }

    private LocalDate latestEffectiveDueDate(WheelIntakeRequestEntity request) {
        return effectiveDueDate(request, latestEffectiveDeadlineAt(request));
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
        OffsetDateTime minimum = minimumBipartiteDeadline(request, availableAt(request), requested);
        return minimum.isAfter(requested) ? minimum : requested;
    }

    private OffsetDateTime minimumBipartiteDeadline(OffsetDateTime available, OffsetDateTime requestedDeadline) {
        return minimumBipartiteDeadline(null, available, requestedDeadline);
    }

    private OffsetDateTime minimumBipartiteDeadline(WheelIntakeRequestEntity request, OffsetDateTime available, OffsetDateTime requestedDeadline) {
        ZoneId zone = zoneFor(request);
        LocalDate date = available.atZoneSameInstant(zone).toLocalDate();
        int remaining = BIPARTITE_MINIMUM_BUSINESS_DAYS;
        while (remaining > 0) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                remaining--;
            }
        }
        return date.atTime(requestedDeadline.atZoneSameInstant(zone).toLocalTime())
                .atZone(zone)
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
        return startRun(ProductionSiteCode.PT, from, to, trigger, actor);
    }

    private PlanningRunEntity startRun(ProductionSiteCode siteCode, LocalDate from, LocalDate to, GenerationTrigger trigger, String actor) {
        PlanningRunEntity run = new PlanningRunEntity();
        run.setProductionSite(productionSites.requireByCode(siteCode));
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
        int totalPlanned = lines.stream().mapToInt(ProductionPlanItemEntity::getQuantity).sum();
        int totalCompleted = lines.stream().mapToInt(ProductionPlanItemEntity::getCompletedQuantity).sum();
        int totalRemaining = lines.stream().mapToInt(ProductionPlanItemEntity::getRemainingQuantity).sum();
        int overtimeQuantity = Math.max(0, totalPlanned - plan.getMaximumTargetSnapshot());
        int belowMinimumQuantity = Math.max(0, plan.getMinimumTargetSnapshot() - totalPlanned);
        int carriedOverQuantity = lines.stream()
                .filter(ProductionPlanItemEntity::isCarriedOver)
                .mapToInt(ProductionPlanItemEntity::getQuantity)
                .sum();
        int advancedQuantity = lines.stream()
                .filter(ProductionPlanItemEntity::isAdvancedFromFuture)
                .mapToInt(ProductionPlanItemEntity::getQuantity)
                .sum();
        int atRiskQuantity = lines.stream()
                .filter(line -> line.getRiskClassification() != RiskClassification.ON_TRACK)
                .mapToInt(ProductionPlanItemEntity::getQuantity)
                .sum();
        return new DailyProductionPlanResponse(
                plan.getId(),
                plan.getPlanningDate(),
                plan.getVersionNumber(),
                plan.getStatus(),
                plan.getMinimumTargetSnapshot(),
                plan.getMaximumTargetSnapshot(),
                wheelQuantityService.toDto(sumLines(lines)),
                totalPlanned,
                totalCompleted,
                totalRemaining,
                belowMinimumQuantity,
                overtimeQuantity,
                carriedOverQuantity,
                advancedQuantity,
                atRiskQuantity,
                overtimeQuantity > 0,
                plan.getWarning(),
                plan.getGeneratedAt(),
                plan.getClosedAt(),
                plan.getClosedBy(),
                plan.getGenerationTrigger(),
                plan.getOptimisticVersion(),
                confirmedPlannedQuantity(lines),
                unconfirmedPlannedQuantity(lines),
                "PLANNED",
                (int) lines.stream().map(line -> line.getRequest().getId()).distinct().count(),
                totalPlanned,
                lines.stream().map(this::toLineResponse).toList()
        );
    }

    private List<ProductionPlanItemEntity> filteredPlanLines(List<ProductionPlanItemEntity> lines,
                                                             String driver,
                                                             UUID customerId,
                                                             LifecycleStatus status,
                                                             WheelType wheelType,
                                                             AvailabilityClassification availability,
                                                             RiskClassification risk) {
        String normalizedDriver = normalizeFilter(driver);
        return lines.stream()
                .filter(line -> normalizedDriver == null || normalizedDriver.equals(normalizeFilter(line.getDriverName())))
                .filter(line -> customerId == null
                        || line.getRequest().getCustomer() != null
                        && customerId.equals(line.getRequest().getCustomer().getId()))
                .filter(line -> status == null || line.getRequest().getLifecycleStatus() == status)
                .filter(line -> wheelType == null || hasPlannedWheelType(line, wheelType))
                .filter(line -> availability == null || line.getAvailabilityClassification() == availability)
                .filter(line -> risk == null || line.getRiskClassification() == risk)
                .toList();
    }

    private boolean hasPlannedWheelType(ProductionPlanItemEntity line, WheelType wheelType) {
        return line.getWheelQuantities().stream()
                .anyMatch(quantity -> quantity.getWheelType() == wheelType && quantity.getPlannedQuantity() > 0);
    }

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private int confirmedPlannedQuantity(List<ProductionPlanItemEntity> lines) {
        return lines.stream()
                .filter(line -> EnumSet.of(LifecycleStatus.AT_FACTORY, LifecycleStatus.IN_PRODUCTION, LifecycleStatus.READY_FOR_PICKUP)
                        .contains(line.getRequest().getLifecycleStatus()))
                .mapToInt(ProductionPlanItemEntity::getQuantity)
                .sum();
    }

    private int unconfirmedPlannedQuantity(List<ProductionPlanItemEntity> lines) {
        return lines.stream()
                .filter(line -> line.getRequest().getLifecycleStatus() == LifecycleStatus.COMMUNICATED)
                .mapToInt(ProductionPlanItemEntity::getQuantity)
                .sum();
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
                line.getClosedAt(),
                line.getClosedBy(),
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

    private ZoneId zoneFor(WheelIntakeRequestEntity request) {
        if (request == null || request.getProductionSite() == null || request.getProductionSite().getTimezone() == null) {
            return businessZone;
        }
        return ZoneId.of(request.getProductionSite().getTimezone());
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
            entity.setProductionSite(plan.getProductionSite());
            entity.setPlan(plan);
            entity.setRequest(request);
            entity.setCustomerName(request.getCustomerNameSnapshot());
            entity.setDriverName(request.getDriver() == null ? "Sem motorista" : request.getDriver().getName());
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
            entity.setRiskClassification(firstProductionDateForAvailability(request, allocation.planningAvailableAt()).isAfter(effectiveDueDate(request, allocation.deadlineAt()))
                    || effectiveDueDate(request, allocation.deadlineAt()).isBefore(plan.getPlanningDate())
                    || plan.getOvertimeQuantity() > 0 ? RiskClassification.AT_RISK : RiskClassification.ON_TRACK);
            entity.setPriorityScore(BigDecimal.valueOf(priority++));
            entity.setPriorityExplanation(priorityExplanation(allocation, request));
            entity.setManuallyPrioritised(request.getManualPriority() != null);
            entity.setLocked(false);
            entity.setCarriedOver(allocation.carriedOver());
            entity.setAdvancedFromFuture(allocation.advancedFromFuture());
            entity.setLineStatus(ProductionPlanLineStatus.OPEN);
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
            if (firstProductionDateForAvailability(request, allocation.planningAvailableAt()).isAfter(effectiveDueDate(request, allocation.deadlineAt()))) {
                parts.add("Prazo impossível de cumprir com as datas fornecidas.");
            }
            if (allocation.deadlineAdjustmentReason() != null) {
                parts.add(allocation.deadlineAdjustmentReason());
            }
            parts.add("Prazo: " + allocation.deadlineAt().atZoneSameInstant(zoneFor(request)).toLocalDate() + ".");
            return String.join(" ", parts);
        }
    }
}
