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
import pt.rucodel.productionplanning.service.ProductionSiteService;
import pt.rucodel.productionplanning.service.WheelIntakeRequestService;

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
    @jakarta.annotation.Resource WheelIntakeRequestService intakeRequests;
    @jakarta.annotation.Resource DriverRepository drivers;
    @jakarta.annotation.Resource CustomerReferenceRepository customers;
    @jakarta.annotation.Resource WheelIntakeRequestRepository requests;
    @jakarta.annotation.Resource ProductionPlanRepository plans;
    @jakarta.annotation.Resource ProductionPlanItemRepository planItems;
    @jakarta.annotation.Resource ProductionPlanItemReconciliationRepository planItemReconciliations;
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
    @jakarta.annotation.Resource ApplicationUserSiteRepository userSites;
    @jakarta.annotation.Resource DriverProductionSiteRepository driverSites;
    @jakarta.annotation.Resource ProductionSiteService productionSites;

    private final ZoneId zone = ZoneId.of("Europe/Lisbon");
    private final LocalDate day = LocalDate.of(2099, 9, 10);
    private DriverEntity driver;
    private CustomerReferenceEntity customerX;
    private CustomerReferenceEntity customerY;
    private CustomerReferenceEntity customerZ;
    private ProductionSiteEntity portugal;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        inboundUpdates.deleteAll();
        conversations.deleteAll();
        drafts.deleteAll();
        capacityAlerts.deleteAll();
        planItemReconciliations.deleteAll();
        planItems.deleteAll();
        plans.deleteAll();
        planningRuns.deleteAll();
        history.deleteAll();
        audit.deleteAll();
        whatsapp.deleteAll();
        requests.deleteAll();
        settings.deleteAll();
        targetConfigurations.deleteAll();
        userSites.deleteAll();
        users.deleteAll();
        customers.deleteAll();
        driverSites.deleteAll();
        drivers.deleteAll();

        portugal = productionSites.requireByCode(ProductionSiteCode.PT);
        driver = drivers.save(driver("Motorista"));
        productionSites.ensureDriverAssociation(driver, portugal,
                DriverProductionSiteAssociationSource.ADMIN, "TEST");
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
    void dailyPlanLookupAppliesCombinedBackendFiltersAndFilteredTotals() {
        createTargets(0, 300, day);
        DriverEntity otherDriver = drivers.save(driver("Outro Motorista"));
        productionSites.ensureDriverAssociation(otherDriver, portugal,
                DriverProductionSiteAssociationSource.ADMIN, "TEST");

        WheelIntakeRequestEntity normal = typedRequest(driver, customerX, Map.of(WheelType.NORMAL, 10),
                day, day.plusDays(5), RequestSource.WEB);
        WheelIntakeRequestEntity washed = typedRequest(otherDriver, customerY, Map.of(WheelType.WASHED, 20),
                day, day.plusDays(5), RequestSource.WEB);

        planning.recalculate(ProductionSiteCode.PT, day, GenerationTrigger.MANUAL, "TEST");

        DailyProductionPlanResponse all = planning.getOrGenerate(ProductionSiteCode.PT, day);
        assertThat(all.lines()).extracting(DailyProductionPlanLineResponse::requestId)
                .containsExactlyInAnyOrder(normal.getId(), washed.getId());
        assertThat(all.totalPlanned()).isEqualTo(30);

        DailyProductionPlanResponse filtered = planning.getOrGenerate(ProductionSiteCode.PT, day,
                "Motorista", customerX.getId(), LifecycleStatus.AT_FACTORY, WheelType.NORMAL,
                AvailabilityClassification.CONFIRMED, RiskClassification.ON_TRACK);

        assertThat(filtered.lines()).singleElement()
                .satisfies(line -> {
                    assertThat(line.requestId()).isEqualTo(normal.getId());
                    assertThat(line.customerId()).isEqualTo(customerX.getId());
                    assertThat(line.driverName()).isEqualTo("Motorista");
                    assertThat(line.plannedQuantity()).isEqualTo(10);
                });
        assertThat(filtered.totalPlanned()).isEqualTo(10);
        assertThat(filtered.confirmedPlannedQuantity()).isEqualTo(10);
        assertThat(filtered.unconfirmedPlannedQuantity()).isZero();

        DailyProductionPlanResponse mismatched = planning.getOrGenerate(ProductionSiteCode.PT, day,
                "Motorista", customerY.getId(), LifecycleStatus.AT_FACTORY, WheelType.NORMAL,
                AvailabilityClassification.CONFIRMED, RiskClassification.ON_TRACK);
        assertThat(mismatched.lines()).isEmpty();
        assertThat(mismatched.totalPlanned()).isZero();

        DailyProductionPlanResponse washedOnly = planning.getOrGenerate(ProductionSiteCode.PT, day,
                null, null, null, WheelType.WASHED, null, null);
        assertThat(washedOnly.lines()).singleElement()
                .extracting(DailyProductionPlanLineResponse::requestId)
                .isEqualTo(washed.getId());
        assertThat(washedOnly.totalPlanned()).isEqualTo(20);
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
    void dailyPlanSeparatesConfirmedAndUnconfirmedPlannedQuantities() {
        createTargets(0, 200, day);
        WheelIntakeRequestEntity confirmed = request(customerX, 30, day, day, RequestSource.WEB);
        WheelIntakeRequestEntity unconfirmed = typedRequest(customerY, Map.of(WheelType.NORMAL, 40),
                day, day, RequestSource.TELEGRAM);
        unconfirmed.setActualFactoryArrivalAt(null);
        unconfirmed.setArrivalConfirmedAt(null);
        unconfirmed.setArrivalConfirmedBy(null);
        unconfirmed.setArrivalConfirmationSource(null);
        unconfirmed.setLifecycleStatus(LifecycleStatus.COMMUNICATED);
        requests.saveAndFlush(unconfirmed);

        DailyProductionPlanResponse plan = planning.recalculate(day, GenerationTrigger.MANUAL, admin);

        assertThat(plan.totalPlanned()).isEqualTo(70);
        assertThat(plan.confirmedPlannedQuantity()).isEqualTo(30);
        assertThat(plan.unconfirmedPlannedQuantity()).isEqualTo(40);
        assertThat(plan.lines()).extracting(DailyProductionPlanLineResponse::requestId)
                .contains(confirmed.getId(), unconfirmed.getId());
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
    void planItemsCanBeClosedIndividuallyWithoutClosingTheWholeDay() {
        createTargets(0, 300, day);
        WheelIntakeRequestEntity x = request(customerX, 10, day, day, RequestSource.WEB);
        WheelIntakeRequestEntity y = request(customerY, 10, day, day, RequestSource.WEB);
        DailyProductionPlanResponse created = planning.recalculate(day, GenerationTrigger.MANUAL, admin);

        DailyProductionPlanLineResponse first = created.lines().stream()
                .filter(line -> line.requestId().equals(x.getId()))
                .findFirst()
                .orElseThrow();
        DailyProductionPlanResponse afterClose = planning.closeItem(day, first.id(),
                closeItemPayload(first, Map.of(WheelType.NORMAL, 10)), admin);

        assertThat(afterClose.status()).isEqualTo(ProductionPlanStatus.AWAITING_RECONCILIATION);
        assertThat(afterClose.lines()).filteredOn(line -> line.requestId().equals(x.getId()))
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.status()).isEqualTo(ProductionPlanLineStatus.CLOSED_COMPLETE);
                    assertThat(line.completedQuantity()).isEqualTo(10);
                    assertThat(line.remainingQuantity()).isZero();
                });
        assertThat(afterClose.lines()).filteredOn(line -> line.requestId().equals(y.getId()))
                .singleElement()
                .extracting(DailyProductionPlanLineResponse::status)
                .isEqualTo(ProductionPlanLineStatus.OPEN);
        assertThat(planning.openReconciliation(day).lines()).extracting(DailyProductionPlanLineResponse::requestId)
                .containsExactly(y.getId());
        assertThat(planning.closedReconciliation(day).lines()).extracting(DailyProductionPlanLineResponse::requestId)
                .containsExactly(x.getId());
        PageResponse<RequestResponse> productionRequests = planning.requestsForProductionDate(day, 0, 10);
        assertThat(productionRequests.content()).extracting(RequestResponse::id)
                .containsExactly(x.getId(), y.getId());
        assertThat(productionRequests.content()).allSatisfy(request -> {
            assertThat(request.productionDate()).isEqualTo(day);
            assertThat(request.productionPlanItemId()).isNotNull();
            assertThat(request.productionLineVersion()).isNotNull();
            assertThat(request.productionPlannedQuantity()).isEqualTo(10);
        });
        assertThat(productionRequests.content()).filteredOn(RequestResponse::canReopenClosure)
                .singleElement()
                .satisfies(request -> {
                    assertThat(request.id()).isEqualTo(x.getId());
                    assertThat(request.reconciliationId()).isNotNull();
                    assertThat(request.closureStatus()).isEqualTo(ProductionPlanItemReconciliationStatus.CLOSED_COMPLETE);
                    assertThat(request.reopenBlockReason()).isNull();
                });
        PageResponse<RequestResponse> allRequests = intakeRequests.listForAdmin(null, null, null, 0, 10);
        assertThat(allRequests.content()).filteredOn(RequestResponse::canReopenClosure)
                .singleElement()
                .satisfies(request -> {
                    assertThat(request.id()).isEqualTo(x.getId());
                    assertThat(request.lifecycleStatus()).isEqualTo(LifecycleStatus.READY_FOR_PICKUP);
                    assertThat(request.productionDate()).isEqualTo(day);
                    assertThat(request.productionPlanItemId()).isEqualTo(first.id());
                    assertThat(request.reconciliationId()).isNotNull();
                });
        assertThat(requests.findById(x.getId()).orElseThrow().getCompletedWheelQuantity()).isEqualTo(10);
        assertThat(requests.findById(y.getId()).orElseThrow().getCompletedWheelQuantity()).isZero();
    }

    @Test
    void readyRequestWithoutReversibleClosureExplainsWhyReopenIsUnavailable() {
        WheelIntakeRequestEntity legacy = request(customerX, 10, day, day, RequestSource.WEB);
        legacy.setLifecycleStatus(LifecycleStatus.READY_FOR_PICKUP);
        requests.saveAndFlush(legacy);

        PageResponse<RequestResponse> allRequests = intakeRequests.listForAdmin(null, null, null, 0, 10);

        assertThat(allRequests.content()).singleElement().satisfies(request -> {
            assertThat(request.id()).isEqualTo(legacy.getId());
            assertThat(request.canReopenClosure()).isFalse();
            assertThat(request.reconciliationId()).isNull();
            assertThat(request.reopenBlockReason()).contains("histórico de fecho");
        });
    }

    @Test
    void planItemClosureRequiresAtLeastOneCompletedWheel() {
        createTargets(0, 300, day);
        WheelIntakeRequestEntity request = request(customerX, 10, day, day.plusDays(1), RequestSource.WEB);
        DailyProductionPlanResponse created = planning.recalculate(day, GenerationTrigger.MANUAL, admin);
        DailyProductionPlanLineResponse line = created.lines().getFirst();

        assertThatThrownBy(() -> planning.closeItem(day, line.id(), closeItemPayload(line, Map.of()), admin))
                .isInstanceOfSatisfying(InvalidRequestException.class, ex ->
                        assertThat(ex.errorCode()).isEqualTo("PLAN_ITEM_NO_COMPLETED_QUANTITY"))
                .hasMessageContaining("pelo menos uma jante concluída");

        assertThat(planItems.findById(line.id())).get().satisfies(openLine -> {
            assertThat(openLine.getLineStatus()).isEqualTo(ProductionPlanLineStatus.OPEN);
            assertThat(openLine.getCompletedQuantity()).isZero();
            assertThat(openLine.getRemainingQuantity()).isEqualTo(10);
        });
        assertThat(requests.findById(request.getId()).orElseThrow()).satisfies(updated -> {
            assertThat(updated.getCompletedWheelQuantity()).isZero();
            assertThat(updated.getLifecycleStatus()).isEqualTo(LifecycleStatus.AT_FACTORY);
        });
        assertThat(planItemReconciliations.findByProductionPlanItemIdOrderByRevisionDesc(line.id())).isEmpty();
    }

    @Test
    void partialPlanItemClosureCreatesCarryOverAndReopenRestoresTheOriginalLine() {
        createTargets(0, 300, day);
        WheelIntakeRequestEntity request = request(customerX, 10, day, day.plusDays(1), RequestSource.WEB);
        DailyProductionPlanResponse created = planning.recalculate(day, GenerationTrigger.MANUAL, admin);
        DailyProductionPlanLineResponse line = created.lines().getFirst();

        DailyProductionPlanResponse partial = planning.closeItem(day, line.id(),
                closeItemPayload(line, Map.of(WheelType.NORMAL, 9)), admin);

        assertThat(partial.lines()).singleElement().satisfies(closed -> {
            assertThat(closed.status()).isEqualTo(ProductionPlanLineStatus.CLOSED_PARTIAL);
            assertThat(closed.completedQuantity()).isEqualTo(9);
            assertThat(closed.remainingQuantity()).isEqualTo(1);
        });
        assertThat(requests.findById(request.getId()).orElseThrow().getCompletedWheelQuantity()).isEqualTo(9);
        assertThat(requests.findById(request.getId()).orElseThrow().getLifecycleStatus()).isEqualTo(LifecycleStatus.IN_PRODUCTION);
        assertThat(plan(day.plusDays(1)).lines()).singleElement().satisfies(carried -> {
            assertThat(carried.carriedOver()).isTrue();
            assertThat(carried.requestId()).isEqualTo(request.getId());
            assertThat(carried.plannedQuantity()).isEqualTo(1);
        });
        assertThat(planning.requestsForProductionDate(day, 0, 10).content())
                .singleElement()
                .satisfies(productionRequest -> {
                    assertThat(productionRequest.canReopenClosure()).isTrue();
                    assertThat(productionRequest.closureStatus()).isEqualTo(ProductionPlanItemReconciliationStatus.CLOSED_PARTIAL);
                    assertThat(productionRequest.reconciliationId()).isNotNull();
                });

        DailyProductionPlanLineResponse closedLine = partial.lines().getFirst();
        DailyProductionPlanResponse reopened = planning.reopenItem(day, line.id(),
                new PlanItemReopenRequest(closedLine.version(), "Quantidade registada incorretamente"), admin);

        assertThat(reopened.lines()).singleElement().satisfies(open -> {
            assertThat(open.status()).isEqualTo(ProductionPlanLineStatus.REOPENED);
            assertThat(open.completedQuantity()).isZero();
            assertThat(open.remainingQuantity()).isEqualTo(10);
        });
        assertThat(planning.openReconciliation(day).lines()).extracting(DailyProductionPlanLineResponse::id)
                .contains(line.id());
        assertThat(requests.findById(request.getId()).orElseThrow().getCompletedWheelQuantity()).isZero();
        assertThat(requests.findById(request.getId()).orElseThrow().getLifecycleStatus()).isEqualTo(LifecycleStatus.AT_FACTORY);
        assertThat(planItemReconciliations.findByProductionPlanItemIdOrderByRevisionDesc(line.id()))
                .singleElement()
                .satisfies(reconciliation -> {
                    assertThat(reconciliation.isReverted()).isTrue();
                    assertThat(reconciliation.getReopenReason()).isEqualTo("Quantidade registada incorretamente");
                });
    }

    @Test
    void planItemReopenIsBlockedWhenLaterCarryOverWasAlreadyClosed() {
        createTargets(0, 300, day);
        request(customerX, 10, day, day.plusDays(1), RequestSource.WEB);
        DailyProductionPlanResponse created = planning.recalculate(day, GenerationTrigger.MANUAL, admin);
        DailyProductionPlanLineResponse original = created.lines().getFirst();
        DailyProductionPlanResponse partial = planning.closeItem(day, original.id(),
                closeItemPayload(original, Map.of(WheelType.NORMAL, 9)), admin);
        DailyProductionPlanLineResponse later = plan(day.plusDays(1)).lines().getFirst();
        planning.closeItem(day.plusDays(1), later.id(),
                closeItemPayload(later, Map.of(WheelType.NORMAL, 1)), admin);

        assertThat(planning.requestsForProductionDate(day, 0, 10).content())
                .singleElement()
                .satisfies(productionRequest -> {
                    assertThat(productionRequest.canReopenClosure()).isFalse();
                    assertThat(productionRequest.reopenBlockReason()).contains("fechos posteriores");
                });

        DailyProductionPlanLineResponse closedOriginal = partial.lines().getFirst();
        assertThatThrownBy(() -> planning.reopenItem(day, original.id(),
                new PlanItemReopenRequest(closedOriginal.version(), "Correção tardia"), admin))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("posterior");
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

    private PlanItemCloseRequest closeItemPayload(DailyProductionPlanLineResponse line, Map<WheelType, Integer> completed) {
        return new PlanItemCloseRequest(line.version(), line.wheelQuantities().stream()
                .map(quantity -> new PlanItemCloseWheelQuantityRequest(quantity.type(),
                        completed.getOrDefault(quantity.type(), 0)))
                .toList(), null);
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
        return typedRequest(driver, customer, quantities, availableDate, dueDate, source);
    }

    private WheelIntakeRequestEntity typedRequest(DriverEntity requestDriver, CustomerReferenceEntity customer, Map<WheelType, Integer> quantities,
                                                  LocalDate availableDate, LocalDate dueDate, RequestSource source) {
        return typedRequest(requestDriver, customer, quantities, availableDate, "09:00", "10:00", dueDate, "18:00", "19:00", source);
    }

    private WheelIntakeRequestEntity typedRequest(CustomerReferenceEntity customer, Map<WheelType, Integer> quantities,
                                                  LocalDate availableDate, String availableStart, String availableEnd,
                                                  LocalDate dueDate, String dueStart, String dueEnd, RequestSource source) {
        return typedRequest(driver, customer, quantities, availableDate, availableStart, availableEnd, dueDate, dueStart, dueEnd, source);
    }

    private WheelIntakeRequestEntity typedRequest(DriverEntity requestDriver, CustomerReferenceEntity customer, Map<WheelType, Integer> quantities,
                                                  LocalDate availableDate, String availableStart, String availableEnd,
                                                  LocalDate dueDate, String dueStart, String dueEnd, RequestSource source) {
        WheelIntakeRequestEntity entity = new WheelIntakeRequestEntity();
        entity.setProductionSite(portugal);
        entity.setSource(source);
        entity.setDriver(requestDriver);
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
        entity.setProductionSite(portugal);
        entity.setName(name);
        entity.setActive(true);
        entity.setCreatedBy("TEST");
        entity.setUpdatedBy("TEST");
        return entity;
    }
}
