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
                .hasMessageContaining("Target mínimo diário não pode ser superior");
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

        assertThat(plan(day).totalPlanned()).isEqualTo(150);
        assertThat(plan(day.plusDays(1)).totalPlanned()).isEqualTo(150);
        assertThat(plan(day.plusDays(2)).totalPlanned()).isEqualTo(150);

        planItems.deleteAll();
        plans.deleteAll();
        requests.deleteAll();
        capacityAlerts.deleteAll();
        WheelIntakeRequestEntity urgent = request(customerY, 90, day, day, RequestSource.WEB);
        WheelIntakeRequestEntity future = request(customerZ, 80, day, day.plusDays(3), RequestSource.TELEGRAM);
        planning.recalculate(day, GenerationTrigger.MANUAL, admin);

        DailyProductionPlanResponse today = plan(day);
        assertThat(today.totalPlanned()).isEqualTo(100);
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
    void deadlinesOverrideBalancingAndOvertimeAlertsUseMaximumTarget() {
        createTargets(100, 200, day);
        request(customerX, 230, day, day, RequestSource.WEB);
        request(customerY, 120, day, day.plusDays(1), RequestSource.WEB);
        request(customerZ, 100, day, day.plusDays(2), RequestSource.WEB);

        planning.recalculate(day, GenerationTrigger.MANUAL, admin);

        assertThat(plan(day).totalPlanned()).isEqualTo(230);
        assertThat(plan(day).overtimeQuantity()).isEqualTo(30);
        assertThat(capacityAlerts.findByStatusInOrderByAffectedDateAscCreatedAtAsc(List.of(CapacityAlertStatus.ACTIVE)))
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.getDeficit()).isEqualTo(30);
                    assertThat(alert.getMessage()).contains("Horas extra necessárias para 2099-09-10");
                });

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
                WheelType.BIPARTITE, 4,
                WheelType.WASHED, 6,
                WheelType.NORMAL, 15
        ), day, day, RequestSource.WEB);

        DailyProductionPlanResponse created = planning.recalculate(day, GenerationTrigger.MANUAL, admin);

        assertThat(created.totalPlanned()).isEqualTo(25);
        assertThat(created.overtimeQuantity()).isEqualTo(3);
        assertThat(created.lines()).singleElement().satisfies(line -> {
            assertThat(line.requestId()).isEqualTo(request.getId());
            assertThat(line.wheelQuantities()).extracting(PlanLineWheelQuantityResponse::plannedQuantity)
                    .containsExactly(4, 6, 15);
        });

        DailyProductionPlanLineResponse line = created.lines().getFirst();
        ReconciliationRequest close = new ReconciliationRequest(created.version(), null, List.of(
                new ReconciliationLineRequest(line.id(), 17, 8, List.of(
                        new ReconciliationLineWheelQuantityRequest(WheelType.BIPARTITE, 3, 1),
                        new ReconciliationLineWheelQuantityRequest(WheelType.WASHED, 4, 2),
                        new ReconciliationLineWheelQuantityRequest(WheelType.NORMAL, 10, 5)
                ), null, line.version())
        ));

        DailyProductionPlanResponse closed = planning.close(day, close, admin);

        assertThat(closed.totalCompleted()).isEqualTo(17);
        assertThat(closed.totalRemaining()).isEqualTo(8);
        WheelIntakeRequestEntity updated = requests.findById(request.getId()).orElseThrow();
        assertThat(updated.completedWheelQuantity(WheelType.BIPARTITE)).isEqualTo(3);
        assertThat(updated.completedWheelQuantity(WheelType.WASHED)).isEqualTo(4);
        assertThat(updated.completedWheelQuantity(WheelType.NORMAL)).isEqualTo(10);

        DailyProductionPlanLineResponse carried = plan(day.plusDays(1)).lines().getFirst();
        assertThat(carried.carriedOver()).isTrue();
        assertThat(carried.wheelQuantities()).extracting(PlanLineWheelQuantityResponse::plannedQuantity)
                .containsExactly(1, 2, 5);
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

        planning.recalculate(day, GenerationTrigger.MANUAL, admin);
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
        WheelIntakeRequestEntity entity = new WheelIntakeRequestEntity();
        entity.setSource(source);
        entity.setDriver(driver);
        entity.setCustomer(customer);
        entity.setCustomerNameSnapshot(customer.getName());
        entity.replaceWheelQuantities(quantities);
        entity.setExpectedFactoryDropOffWindowStart(at(availableDate, "09:00"));
        entity.setExpectedFactoryDropOffWindowEnd(at(availableDate, "10:00"));
        entity.setRequestedFactoryPickupWindowStart(at(dueDate, "18:00"));
        entity.setRequestedFactoryPickupWindowEnd(at(dueDate, "19:00"));
        entity.setLifecycleStatus(LifecycleStatus.REGISTERED);
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
