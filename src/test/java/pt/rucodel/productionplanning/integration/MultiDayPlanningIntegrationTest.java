package pt.rucodel.productionplanning.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pt.rucodel.productionplanning.domain.*;
import pt.rucodel.productionplanning.dto.*;
import pt.rucodel.productionplanning.entity.*;
import pt.rucodel.productionplanning.exception.InvalidRequestException;
import pt.rucodel.productionplanning.repository.*;
import pt.rucodel.productionplanning.security.AuthenticatedUser;
import pt.rucodel.productionplanning.service.MultiDayProductionPlanningService;
import pt.rucodel.productionplanning.service.ProductionTargetService;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MultiDayPlanningIntegrationTest {
    @jakarta.annotation.Resource MultiDayProductionPlanningService planning;
    @jakarta.annotation.Resource ProductionTargetService targets;
    @jakarta.annotation.Resource DriverRepository drivers;
    @jakarta.annotation.Resource CustomerReferenceRepository customers;
    @jakarta.annotation.Resource WheelIntakeRequestRepository requests;
    @jakarta.annotation.Resource ProductionPlanRepository plans;
    @jakarta.annotation.Resource ProductionPlanItemRepository planItems;
    @jakarta.annotation.Resource CapacityAlertRepository capacityAlerts;
    @jakarta.annotation.Resource ProductionTargetConfigurationRepository targetConfigurations;
    @jakarta.annotation.Resource PlanningRunRepository planningRuns;
    @jakarta.annotation.Resource DailyProductionSettingsRepository settings;
    @jakarta.annotation.Resource RequestStatusHistoryRepository history;
    @jakarta.annotation.Resource PlanningAuditEventRepository audit;
    @jakarta.annotation.Resource TelegramConversationRepository conversations;
    @jakarta.annotation.Resource TelegramIntakeDraftRepository drafts;
    @jakarta.annotation.Resource TelegramInboundUpdateRepository inboundUpdates;
    @jakarta.annotation.Resource WhatsAppIngestionItemRepository whatsapp;
    @jakarta.annotation.Resource ApplicationUserRepository users;

    private final ZoneId zone = ZoneId.of("Europe/Lisbon");
    private final LocalDate day = LocalDate.of(2099, 9, 10);
    private DriverEntity driver;
    private CustomerReferenceEntity customerX;
    private CustomerReferenceEntity customerY;
    private CustomerReferenceEntity customerZ;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        inboundUpdates.deleteAll();
        conversations.deleteAll();
        drafts.deleteAll();
        capacityAlerts.deleteAll();
        planItems.deleteAll();
        plans.deleteAll();
        planningRuns.deleteAll();
        history.deleteAll();
        audit.deleteAll();
        whatsapp.deleteAll();
        requests.deleteAll();
        settings.deleteAll();
        targetConfigurations.deleteAll();
        users.deleteAll();
        customers.deleteAll();
        drivers.deleteAll();

        driver = drivers.save(driver("Motorista"));
        customerX = customers.save(customer("Cliente X"));
        customerY = customers.save(customer("Cliente Y"));
        customerZ = customers.save(customer("Cliente Z"));
        admin = new AuthenticatedUser(UUID.randomUUID(), "admin", "Administrador", UserRole.ADMIN, null);
    }

    @Test
    void targetMinimumAboveMaximumIsRejected() {
        assertThatThrownBy(() -> targets.create(new PlanningTargetRequest(201, 200, day), "TEST"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("O target mínimo não pode ser superior");
    }

    @Test
    void defaultTargetsAreUsedWhenNoConfigurationExists() {
        assertThat(targetConfigurations.findAll()).isEmpty();
        ProductionTargetConfigurationEntity fallback = targets.effectiveFor(day);
        assertThat(fallback.getMinimumDailyTarget()).isEqualTo(150);
        assertThat(fallback.getRegularDailyCapacity()).isEqualTo(180);
        assertThat(fallback.isSystemDefault()).isTrue();

        request(customerX, 80, day, day.plusDays(1), RequestSource.WEB);
        DailyProductionPlanResponse plan = planning.recalculate(day, GenerationTrigger.MANUAL, admin);

        assertThat(plan.minimumDailyTarget()).isEqualTo(150);
        assertThat(plan.regularDailyCapacity()).isEqualTo(180);
        assertThat(plan.totalPlanned()).isEqualTo(80);
        assertThat(plan.warning()).contains("Target mínimo não atingido");
    }

    @Test
    void defaultTargetsAreReducedByHalfOnSaturday() {
        assertThat(targetConfigurations.findAll()).isEmpty();
        LocalDate saturday = LocalDate.of(2099, 9, 12);
        request(customerX, 95, saturday, saturday, RequestSource.TELEGRAM);

        DailyProductionPlanResponse saturdayPlan = planning.recalculate(saturday, GenerationTrigger.MANUAL, admin);

        assertThat(saturdayPlan.minimumDailyTarget()).isEqualTo(75);
        assertThat(saturdayPlan.regularDailyCapacity()).isEqualTo(90);
        assertThat(saturdayPlan.totalPlanned()).isEqualTo(95);
        assertThat(saturdayPlan.overtimeQuantity()).isEqualTo(5);
    }

    @Test
    void savingTargetsDoesNotRecalculateAndManualRecalculationUsesLatestTargets() {
        createTargets(10, 20, day);
        request(customerX, 20, day, day, RequestSource.WEB);

        DailyProductionPlanResponse initial = planning.recalculate(day, GenerationTrigger.MANUAL, admin);

        assertThat(initial.minimumDailyTarget()).isEqualTo(10);
        assertThat(initial.regularDailyCapacity()).isEqualTo(20);
        assertThat(initial.overtimeQuantity()).isZero();

        createTargets(5, 7, day);

        DailyProductionPlanResponse stillSnapshot = plan(day);
        assertThat(stillSnapshot.minimumDailyTarget()).isEqualTo(10);
        assertThat(stillSnapshot.regularDailyCapacity()).isEqualTo(20);

        DailyProductionPlanResponse recalculated = planning.recalculate(day, GenerationTrigger.MANUAL, admin);
        assertThat(recalculated.minimumDailyTarget()).isEqualTo(5);
        assertThat(recalculated.regularDailyCapacity()).isEqualTo(7);
        assertThat(recalculated.overtimeQuantity()).isEqualTo(13);
    }

    @Test
    void urgentWorkPendingWorkAvailabilityMinimumAndBalancedDistributionAreHandled() {
        createTargets(100, 200, day);
        request(customerX, 450, day, day.plusDays(2), RequestSource.WEB);

        planning.recalculate(day, GenerationTrigger.MANUAL, admin);

        assertThat(plan(day).totalPlanned()).isEqualTo(200);
        assertThat(plan(day.plusDays(1)).totalPlanned()).isEqualTo(200);
        assertThat(plan(day.plusDays(2)).totalPlanned()).isEqualTo(50);

        planItems.deleteAll();
        plans.deleteAll();
        requests.deleteAll();
        capacityAlerts.deleteAll();
        WheelIntakeRequestEntity urgent = request(customerY, 90, day, day, RequestSource.WEB);
        WheelIntakeRequestEntity future = request(customerZ, 80, day, day.plusDays(3), RequestSource.TELEGRAM);
        planning.recalculate(day, GenerationTrigger.MANUAL, admin);

        DailyProductionPlanResponse today = plan(day);
        assertThat(today.totalPlanned()).isEqualTo(170);
        assertThat(today.lines()).extracting(DailyProductionPlanLineResponse::requestId)
                .startsWith(urgent.getId());
        assertThat(today.lines()).anySatisfy(line -> {
            assertThat(line.requestId()).isEqualTo(future.getId());
            assertThat(line.advancedFromFuture()).isTrue();
        });

        planItems.deleteAll();
        plans.deleteAll();
        requests.deleteAll();
        capacityAlerts.deleteAll();
        WheelIntakeRequestEntity lateArrival = request(customerX, 70, day.plusDays(1), day.plusDays(2), RequestSource.WEB);
        planning.recalculate(day, GenerationTrigger.MANUAL, admin);

        assertThat(plan(day).totalPlanned()).isZero();
        assertThat(plan(day).warning()).contains("Target mínimo não atingido");
        assertThat(plan(day.plusDays(1)).lines()).extracting(DailyProductionPlanLineResponse::requestId)
                .contains(lateArrival.getId());
    }

    @Test
    void telegramRequestWithWashedAndNormalWheelsIsPlannedToMaximumThenRemainder() {
        LocalDate firstDay = LocalDate.of(2026, 9, 24);
        LocalDate secondDay = LocalDate.of(2026, 9, 25);
        createTargets(150, 180, firstDay);
        WheelIntakeRequestEntity request = typedRequest(customerX, Map.of(
                WheelType.BIPARTITE, 0,
                WheelType.WASHED, 30,
                WheelType.NORMAL, 200
        ), firstDay, "05:30", "06:00", secondDay, "09:00", "14:00", RequestSource.TELEGRAM);
        request.setCustomerNameSnapshot("teste");
        request = requests.saveAndFlush(request);
        UUID requestId = request.getId();

        planning.recalculate(firstDay, GenerationTrigger.MANUAL, admin);

        DailyProductionPlanResponse day24 = plan(firstDay);
        DailyProductionPlanResponse day25 = plan(secondDay);
        assertThat(day24.totalPlanned()).isEqualTo(180);
        assertThat(day24.minimumDailyTarget()).isEqualTo(150);
        assertThat(day24.regularDailyCapacity()).isEqualTo(180);
        assertThat(day24.overtimeQuantity()).isZero();
        assertThat(day24.lines()).singleElement().satisfies(line -> {
            assertThat(line.requestId()).isEqualTo(requestId);
            assertThat(line.source()).isEqualTo(RequestSource.TELEGRAM);
            assertThat(line.bipartiteQuantity()).isZero();
            assertThat(line.washedQuantity()).isEqualTo(30);
            assertThat(line.normalQuantity()).isEqualTo(150);
            assertThat(line.totalQuantity()).isEqualTo(180);
            assertThat(line.availableAt()).isEqualTo(at(firstDay, "06:00"));
            assertThat(line.deadlineAt()).isEqualTo(at(secondDay, "09:00"));
        });

        assertThat(day25.totalPlanned()).isEqualTo(50);
        assertThat(day25.minimumDailyTarget()).isEqualTo(150);
        assertThat(day25.regularDailyCapacity()).isEqualTo(180);
        assertThat(day25.overtimeQuantity()).isZero();
        assertThat(day25.warning()).contains("Target mínimo não atingido");
        assertThat(day25.lines()).singleElement().satisfies(line -> {
            assertThat(line.requestId()).isEqualTo(requestId);
            assertThat(line.bipartiteQuantity()).isZero();
            assertThat(line.washedQuantity()).isZero();
            assertThat(line.normalQuantity()).isEqualTo(50);
            assertThat(line.totalQuantity()).isEqualTo(50);
        });

        assertThat(day24.wheelQuantities()).extracting(WheelQuantityDto::quantity).containsExactly(0, 30, 150);
        assertThat(day25.wheelQuantities()).extracting(WheelQuantityDto::quantity).containsExactly(0, 0, 50);
    }

    @Test
    void bipartiteDeadlinesAreAdjustedByFifteenBusinessDaysWithoutChangingOtherTypes() {
        createTargets(0, 200, day);
        WheelIntakeRequestEntity request = typedRequest(customerX, Map.of(
                WheelType.BIPARTITE, 5,
                WheelType.WASHED, 7,
                WheelType.NORMAL, 9
        ), day, "09:00", "10:00", day.plusDays(1), "09:00", "14:00", RequestSource.WEB);

        planning.recalculate(day, GenerationTrigger.MANUAL, admin);

        DailyProductionPlanResponse initial = plan(day);
        assertThat(initial.lines()).singleElement().satisfies(line -> {
            assertThat(line.washedQuantity()).isEqualTo(7);
            assertThat(line.normalQuantity()).isEqualTo(9);
            assertThat(line.bipartiteQuantity()).isZero();
            assertThat(line.deadlineAt()).isEqualTo(at(day.plusDays(1), "09:00"));
        });

        LocalDate adjustedDate = LocalDate.of(2099, 10, 1);
        DailyProductionPlanResponse adjusted = plan(adjustedDate);
        assertThat(adjusted.lines()).singleElement().satisfies(line -> {
            assertThat(line.requestId()).isEqualTo(request.getId());
            assertThat(line.bipartiteQuantity()).isEqualTo(5);
            assertThat(line.washedQuantity()).isZero();
            assertThat(line.normalQuantity()).isZero();
            assertThat(line.deadlineAt()).isEqualTo(at(adjustedDate, "09:00"));
            assertThat(line.priorityExplanation()).contains("Prazo mínimo de 15 dias úteis");
        });
        WheelIntakeRequestEntity persisted = requests.findById(request.getId()).orElseThrow();
        assertThat(persisted.getWheelQuantities()).anySatisfy(quantity -> {
            assertThat(quantity.getWheelType()).isEqualTo(WheelType.BIPARTITE);
            assertThat(quantity.getRequestedDeadlineAt()).isEqualTo(at(day.plusDays(1), "09:00"));
            assertThat(quantity.getEffectiveDeadlineAt()).isEqualTo(at(adjustedDate, "09:00"));
            assertThat(quantity.getDeadlineAdjustmentReason()).contains("15 dias úteis");
        });
    }

    @Test
    void deadlinesOverrideBalancingAndOvertimeAlertsUseMaximumTarget() {
        createTargets(100, 200, day);
        request(customerX, 230, day, day, RequestSource.WEB);
        request(customerY, 120, day, day.plusDays(1), RequestSource.WEB);
        request(customerZ, 100, day, day.plusDays(2), RequestSource.WEB);

        planning.recalculate(day, GenerationTrigger.MANUAL, admin);

        assertThat(plan(day).totalPlanned()).isEqualTo(230);
        assertThat(plan(day).overtimeQuantity()).isEqualTo(30);
        assertThat(plan(day).warning()).contains("Horas extra necessárias", "Excesso estimado: 30");
        assertThat(capacityAlerts.findAll()).isEmpty();

        planItems.deleteAll();
        plans.deleteAll();
        requests.deleteAll();
        capacityAlerts.deleteAll();
        request(customerX, 200, day, day, RequestSource.WEB);
        planning.recalculate(day, GenerationTrigger.MANUAL, admin);
        assertThat(plan(day).overtimeQuantity()).isZero();
        assertThat(capacityAlerts.findAll()).isEmpty();
    }

    @Test
    void productionCalendarAppliesSaturdayHalfDayAndSkipsSunday() {
        createTargets(100, 200, day);
        LocalDate saturday = LocalDate.of(2099, 9, 12);
        LocalDate sunday = LocalDate.of(2099, 9, 13);
        LocalDate monday = LocalDate.of(2099, 9, 14);

        request(customerX, 125, saturday, saturday, RequestSource.TELEGRAM);
        planning.recalculate(saturday, GenerationTrigger.MANUAL, admin);

        DailyProductionPlanResponse saturdayPlan = plan(saturday);
        assertThat(saturdayPlan.minimumDailyTarget()).isEqualTo(50);
        assertThat(saturdayPlan.regularDailyCapacity()).isEqualTo(100);
        assertThat(saturdayPlan.totalPlanned()).isEqualTo(125);
        assertThat(saturdayPlan.overtimeQuantity()).isEqualTo(25);
        assertThat(saturdayPlan.warning()).contains("Excesso estimado: 25");

        request(customerY, 30, sunday, monday, RequestSource.WEB);
        planning.recalculate(sunday, GenerationTrigger.MANUAL, admin);

        DailyProductionPlanResponse sundayPlan = plan(sunday);
        assertThat(sundayPlan.lines()).isEmpty();
        assertThat(sundayPlan.warning()).contains("Domingo não é dia de produção");
        assertThat(plans.findFirstByPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(sunday)).isEmpty();
        assertThat(plan(monday).lines()).extracting(DailyProductionPlanLineResponse::customerName)
                .contains("Cliente Y");
    }

    @Test
    void saturdayAfternoonArrivalsMoveToMondayAndSundayDeadlinesMoveToSaturday() {
        createTargets(0, 200, day);
        LocalDate saturday = LocalDate.of(2099, 9, 12);
        LocalDate sunday = LocalDate.of(2099, 9, 13);
        LocalDate monday = LocalDate.of(2099, 9, 14);

        WheelIntakeRequestEntity saturdayAfternoon = typedRequest(customerX, Map.of(WheelType.NORMAL, 20),
                saturday, "15:00", "15:30", monday, "18:00", "19:00", RequestSource.WEB);
        WheelIntakeRequestEntity saturdayClosing = typedRequest(customerZ, Map.of(WheelType.NORMAL, 5),
                saturday, "12:30", "13:00", monday, "18:00", "19:00", RequestSource.WEB);
        WheelIntakeRequestEntity sundayDeadline = typedRequest(customerY, Map.of(WheelType.NORMAL, 10),
                saturday, "09:00", "10:00", sunday, "12:00", "13:00", RequestSource.WEB);

        planning.recalculate(saturday, GenerationTrigger.MANUAL, admin);

        assertThat(plan(saturday).lines()).extracting(DailyProductionPlanLineResponse::requestId)
                .contains(sundayDeadline.getId())
                .doesNotContain(saturdayAfternoon.getId(), saturdayClosing.getId());
        assertThat(plan(monday).lines()).extracting(DailyProductionPlanLineResponse::requestId)
                .contains(saturdayAfternoon.getId(), saturdayClosing.getId());
    }

    @Test
    void weekdayArrivalsAfterProductionWindowMoveToNextProductionDay() {
        createTargets(0, 200, day);

        WheelIntakeRequestEntity lateWeekday = typedRequest(customerX, Map.of(WheelType.NORMAL, 10),
                day, "21:30", "22:00", day.plusDays(1), "18:00", "19:00", RequestSource.WEB);
        WheelIntakeRequestEntity afterHours = typedRequest(customerY, Map.of(WheelType.NORMAL, 15),
                day, "22:00", "22:30", day.plusDays(1), "18:00", "19:00", RequestSource.WEB);

        planning.recalculate(day, GenerationTrigger.MANUAL, admin);

        assertThat(plan(day).lines()).extracting(DailyProductionPlanLineResponse::requestId)
                .doesNotContain(lateWeekday.getId(), afterHours.getId());
        assertThat(plan(day.plusDays(1)).lines()).extracting(DailyProductionPlanLineResponse::requestId)
                .contains(lateWeekday.getId(), afterHours.getId());
    }

    @Test
    void impossibleDeadlineIsExplicitlyFlaggedInsteadOfBeingOmitted() {
        createTargets(0, 200, day);
        LocalDate sunday = LocalDate.of(2099, 9, 13);
        LocalDate monday = LocalDate.of(2099, 9, 14);
        WheelIntakeRequestEntity impossible = request(customerX, 12, sunday, sunday, RequestSource.TELEGRAM);

        planning.recalculate(sunday, GenerationTrigger.MANUAL, admin);

        DailyProductionPlanResponse mondayPlan = plan(monday);
        assertThat(mondayPlan.lines()).extracting(DailyProductionPlanLineResponse::requestId)
                .contains(impossible.getId());
        assertThat(mondayPlan.warning()).contains("Prazo impossível");
        assertThat(mondayPlan.lines()).anySatisfy(line -> {
            assertThat(line.requestId()).isEqualTo(impossible.getId());
            assertThat(line.riskClassification()).isEqualTo(RiskClassification.AT_RISK);
        });
    }

    @Test
    void closureStoresRealityCarriesPendingWorkAndIsIdempotent() {
        createTargets(0, 300, day);
        WheelIntakeRequestEntity x = request(customerX, 30, day, day, RequestSource.WEB);
        WheelIntakeRequestEntity y = request(customerY, 50, day, day, RequestSource.WEB);
        WheelIntakeRequestEntity z = request(customerZ, 100, day, day, RequestSource.TELEGRAM);
        DailyProductionPlanResponse created = planning.recalculate(day, GenerationTrigger.MANUAL, admin);

        ReconciliationRequest close = closePayload(created, List.of(25, 50, 80));
        DailyProductionPlanResponse closed = planning.close(day, close, admin);

        assertThat(closed.totalPlanned()).isEqualTo(180);
        assertThat(closed.totalCompleted()).isEqualTo(155);
        assertThat(closed.totalRemaining()).isEqualTo(25);
        assertThat(requests.count()).isEqualTo(3);
        assertThat(requests.findById(x.getId()).orElseThrow().getCompletedWheelQuantity()).isEqualTo(25);
        assertThat(requests.findById(y.getId()).orElseThrow().getCompletedWheelQuantity()).isEqualTo(50);
        assertThat(requests.findById(z.getId()).orElseThrow().getCompletedWheelQuantity()).isEqualTo(80);

        DailyProductionPlanResponse next = plan(day.plusDays(1));
        assertThat(next.totalPlanned()).isEqualTo(25);
        assertThat(next.lines()).allSatisfy(line -> {
            assertThat(line.carriedOver()).isTrue();
            assertThat(line.deadlineAt()).isNotNull();
        });

        planning.close(day, close, admin);
        assertThat(requests.findById(x.getId()).orElseThrow().getCompletedWheelQuantity()).isEqualTo(25);
        assertThat(requests.findById(z.getId()).orElseThrow().getCompletedWheelQuantity()).isEqualTo(80);
    }

    @Test
    void planningClosureAndCarryOverKeepWheelTypeDetailsAndTargetsUseAggregateTotal() {
        createTargets(20, 22, day);
        WheelIntakeRequestEntity request = typedRequest(customerX, Map.of(
                WheelType.BIPARTITE, 0,
                WheelType.WASHED, 6,
                WheelType.NORMAL, 19
        ), day, day, RequestSource.WEB);

        DailyProductionPlanResponse created = planning.recalculate(day, GenerationTrigger.MANUAL, admin);

        assertThat(created.totalPlanned()).isEqualTo(25);
        assertThat(created.overtimeQuantity()).isEqualTo(3);
        assertThat(created.lines()).singleElement().satisfies(line -> {
            assertThat(line.requestId()).isEqualTo(request.getId());
            assertThat(line.wheelQuantities()).extracting(PlanLineWheelQuantityResponse::plannedQuantity)
                    .containsExactly(0, 6, 19);
        });

        DailyProductionPlanLineResponse line = created.lines().getFirst();
        ReconciliationRequest close = new ReconciliationRequest(created.version(), null, List.of(
                new ReconciliationLineRequest(line.id(), 17, 8, List.of(
                        new ReconciliationLineWheelQuantityRequest(WheelType.BIPARTITE, 0, 0),
                        new ReconciliationLineWheelQuantityRequest(WheelType.WASHED, 4, 2),
                        new ReconciliationLineWheelQuantityRequest(WheelType.NORMAL, 13, 6)
                ), null, line.version())
        ));

        DailyProductionPlanResponse closed = planning.close(day, close, admin);

        assertThat(closed.totalCompleted()).isEqualTo(17);
        assertThat(closed.totalRemaining()).isEqualTo(8);
        WheelIntakeRequestEntity updated = requests.findById(request.getId()).orElseThrow();
        assertThat(updated.completedWheelQuantity(WheelType.BIPARTITE)).isZero();
        assertThat(updated.completedWheelQuantity(WheelType.WASHED)).isEqualTo(4);
        assertThat(updated.completedWheelQuantity(WheelType.NORMAL)).isEqualTo(13);

        DailyProductionPlanLineResponse carried = plan(day.plusDays(1)).lines().getFirst();
        assertThat(carried.carriedOver()).isTrue();
        assertThat(carried.wheelQuantities()).extracting(PlanLineWheelQuantityResponse::plannedQuantity)
                .containsExactly(0, 2, 6);
        assertThat(requests.count()).isEqualTo(1);
    }

    @Test
    void incompleteInconsistentClosureReopenAndClosedPlanProtectionWork() {
        createTargets(0, 300, day);
        request(customerX, 10, day, day, RequestSource.WEB);
        DailyProductionPlanResponse created = planning.recalculate(day, GenerationTrigger.MANUAL, admin);
        DailyProductionPlanLineResponse line = created.lines().getFirst();

        assertThatThrownBy(() -> planning.close(day, new ReconciliationRequest(created.version(), null,
                List.of(new ReconciliationLineRequest(line.id(), 9, 9, null, null, line.version()))), admin))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("planeado");

        DailyProductionPlanResponse closed = planning.close(day, closePayload(created, List.of(10)), admin);
        assertThat(closed.status()).isEqualTo(ProductionPlanStatus.CLOSED);

        assertThatThrownBy(() -> planning.recalculate(day, GenerationTrigger.MANUAL, admin))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("já está fechado");
        assertThat(plan(day).versionNumber()).isEqualTo(closed.versionNumber());

        assertThatThrownBy(() -> planning.reopen(day, new ReopenPlanRequest(closed.version(), " "), admin))
                .isInstanceOf(InvalidRequestException.class);

        DailyProductionPlanResponse reopened = planning.reopen(day, new ReopenPlanRequest(closed.version(), "Correção de fecho"), admin);
        assertThat(reopened.status()).isEqualTo(ProductionPlanStatus.AWAITING_RECONCILIATION);
    }

    @Test
    void nextPlanIsProvisionalUntilPreviousDayIsClosedAndOptimisticLockingProtectsClosure() {
        createTargets(0, 300, day);
        request(customerX, 10, day, day.plusDays(1), RequestSource.WEB);
        DailyProductionPlanResponse created = planning.recalculate(day, GenerationTrigger.MANUAL, admin);

        assertThat(plan(day.plusDays(1)).status()).isEqualTo(ProductionPlanStatus.PROVISIONAL);

        assertThatThrownBy(() -> planning.saveReconciliation(day, new ReconciliationRequest(999L, null, List.of()), admin))
                .isInstanceOf(InvalidRequestException.class);

        planning.close(day, closePayload(created, List.of(10)), admin);
        assertThat(plan(day.plusDays(1)).status()).isEqualTo(ProductionPlanStatus.PUBLISHED);
    }

    private ReconciliationRequest closePayload(DailyProductionPlanResponse plan, List<Integer> completed) {
        assertThat(plan.lines()).hasSize(completed.size());
        List<ReconciliationLineRequest> lines = new java.util.ArrayList<>();
        for (int i = 0; i < plan.lines().size(); i++) {
            DailyProductionPlanLineResponse line = plan.lines().get(i);
            int done = completed.get(i);
            lines.add(new ReconciliationLineRequest(line.id(), done, line.plannedQuantity() - done, null, null, line.version()));
        }
        return new ReconciliationRequest(plan.version(), null, lines);
    }

    private DailyProductionPlanResponse plan(LocalDate date) {
        return planning.getOrGenerate(date);
    }

    private void createTargets(int minimum, int maximum, LocalDate effectiveFrom) {
        targets.create(new PlanningTargetRequest(minimum, maximum, effectiveFrom), "TEST");
    }

    private WheelIntakeRequestEntity request(CustomerReferenceEntity customer, int quantity, LocalDate availableDate,
                                             LocalDate dueDate, RequestSource source) {
        return typedRequest(customer, Map.of(WheelType.NORMAL, quantity), availableDate, dueDate, source);
    }

    private WheelIntakeRequestEntity typedRequest(CustomerReferenceEntity customer, Map<WheelType, Integer> quantities,
                                                  LocalDate availableDate, LocalDate dueDate, RequestSource source) {
        return typedRequest(customer, quantities, availableDate, "09:00", "10:00", dueDate, "18:00", "19:00", source);
    }

    private WheelIntakeRequestEntity typedRequest(CustomerReferenceEntity customer, Map<WheelType, Integer> quantities,
                                                  LocalDate availableDate, String availableStart, String availableEnd,
                                                  LocalDate dueDate, String dueStart, String dueEnd, RequestSource source) {
        WheelIntakeRequestEntity entity = new WheelIntakeRequestEntity();
        entity.setSource(source);
        entity.setDriver(driver);
        entity.setCustomer(customer);
        entity.setCustomerNameSnapshot(customer.getName());
        entity.replaceWheelQuantities(quantities);
        entity.setExpectedFactoryDropOffWindowStart(at(availableDate, availableStart));
        entity.setExpectedFactoryDropOffWindowEnd(at(availableDate, availableEnd));
        entity.setActualFactoryArrivalAt(at(availableDate, availableEnd));
        entity.setArrivalConfirmedAt(at(availableDate, availableEnd));
        entity.setArrivalConfirmedBy("TEST");
        entity.setArrivalConfirmationSource("TEST");
        entity.setRequestedFactoryPickupWindowStart(at(dueDate, dueStart));
        entity.setRequestedFactoryPickupWindowEnd(at(dueDate, dueEnd));
        entity.setLifecycleStatus(LifecycleStatus.AT_FACTORY);
        entity.setCreatedBy("TEST");
        entity.setUpdatedBy("TEST");
        return requests.saveAndFlush(entity);
    }

    private OffsetDateTime at(LocalDate date, String time) {
        return date.atTime(LocalTime.parse(time)).atZone(zone).toOffsetDateTime();
    }

    private DriverEntity driver(String name) {
        DriverEntity entity = new DriverEntity();
        entity.setName(name);
        entity.setActive(true);
        entity.setCreatedBy("TEST");
        entity.setUpdatedBy("TEST");
        return entity;
    }

    private CustomerReferenceEntity customer(String name) {
        CustomerReferenceEntity entity = new CustomerReferenceEntity();
        entity.setName(name);
        entity.setActive(true);
        entity.setCreatedBy("TEST");
        entity.setUpdatedBy("TEST");
        return entity;
    }
}
