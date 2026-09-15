package pt.rucodel.productionplanning.service;

import org.junit.jupiter.api.Test;
import pt.rucodel.productionplanning.domain.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningEngineTest {
    private final PlanningEngine engine = new PlanningEngine();
    private final ZoneId lisbon = ZoneId.of("Europe/Lisbon");
    private final LocalDate day = LocalDate.of(2026, 9, 2);
    private final OffsetDateTime now = at(day, "07:00");

    @Test
    void earlierFactoryPickupDeadlinesReceiveHigherPriority() {
        PlanningResult result = plan(
                item("later", 4, "09:00", "17:00"),
                item("earlier", 4, "09:00", "12:00")
        );

        assertThat(result.items()).extracting(PlanningItemResult::requestId)
                .containsExactly(id("earlier"), id("later"));
    }

    @Test
    void negativeSlackIsPrioritisedCorrectly() {
        PlanningResult result = plan(
                item("normal", 2, "09:00", "17:00", Duration.ofMinutes(60)),
                item("negative-slack", 8, "09:00", "10:00", Duration.ofHours(4))
        );

        assertThat(result.items().getFirst().requestId()).isEqualTo(id("negative-slack"));
        assertThat(result.items().getFirst().riskClassification()).isEqualTo(RiskClassification.AT_RISK);
    }

    @Test
    void wheelsAreNeverScheduledBeforeFactoryArrival() {
        PlanningResult result = plan(item("lunch", 2, "14:00", "18:00", Duration.ofMinutes(60)));

        PlanningItemResult item = result.items().getFirst();
        assertThat(item.assignedWindowLabel()).isEqualTo("Meio da tarde");
        assertThat(item.availabilityAt()).isEqualTo(at(day, "14:00"));
    }

    @Test
    void lunchArrivalCanOnlyEnterCompatibleLaterProductionWindow() {
        PlanningResult result = plan(item("lunch", 2, "14:00", "18:00", Duration.ofMinutes(60)));

        assertThat(result.items().getFirst().assignedWindowLabel()).isEqualTo("Meio da tarde");
    }

    @Test
    void endOfDayArrivalIsDeferredToNextSuitableDay() {
        PlanningResult result = plan(item("late", 3, "21:00", "09:00", day.plusDays(1), Duration.ofMinutes(60)));

        PlanningItemResult item = result.items().getFirst();
        assertThat(item.assignedProductionDate()).isNull();
        assertThat(item.accountingBucket()).isEqualTo(AccountingBucket.FUTURE_WORKLOAD);
    }

    @Test
    void physicallyReceivedWheelsUseActualArrivalTime() {
        PlanningWorkItem received = item("received", 4, "14:00", "18:00", Duration.ofMinutes(60),
                at(day, "08:15"), LifecycleStatus.ARRIVED_AT_FACTORY, null, false, null, null);

        PlanningItemResult item = plan(received).items().getFirst();

        assertThat(item.availabilityAt()).isEqualTo(at(day, "08:15"));
        assertThat(item.availabilityClassification()).isEqualTo(AvailabilityClassification.CONFIRMED);
    }

    @Test
    void expectedWheelsAreMarkedTentative() {
        PlanningItemResult item = plan(item("tentative", 4, "11:00", "18:00")).items().getFirst();

        assertThat(item.availabilityClassification()).isEqualTo(AvailabilityClassification.TENTATIVE);
    }

    @Test
    void missingInformationIsVisibleAndNeverSilentlyIgnored() {
        PlanningWorkItem missing = new PlanningWorkItem(id("missing"), "Cliente", "Motorista", 4, now,
                at(day, "08:00"), null, null, null, LifecycleStatus.REGISTERED, null,
                false, null, null, Duration.ofHours(1), true);

        PlanningResult result = plan(missing);

        assertThat(result.items().getFirst().riskClassification()).isEqualTo(RiskClassification.MISSING_INFORMATION);
        assertThat(result.accountingTotals().atRisk()).isEqualTo(4);
    }

    @Test
    void lockedAdministratorDecisionsSurviveRegeneration() {
        PlanningWorkItem locked = item("locked", 2, "08:00", "18:00", Duration.ofMinutes(60),
                null, LifecycleStatus.REGISTERED, null, true, day, "Fim do dia");

        PlanningItemResult item = plan(locked).items().getFirst();

        assertThat(item.locked()).isTrue();
        assertThat(item.assignedProductionDate()).isEqualTo(day);
        assertThat(item.assignedWindowLabel()).isEqualTo("Fim do dia");
    }

    @Test
    void manualPriorityIsRespected() {
        PlanningWorkItem normal = item("normal", 2, "08:00", "12:00");
        PlanningWorkItem manual = item("manual", 2, "10:00", "18:00", Duration.ofMinutes(30),
                null, LifecycleStatus.REGISTERED, 1, false, null, null);

        PlanningResult result = plan(normal, manual);

        assertThat(result.items().getFirst().requestId()).isEqualTo(id("manual"));
        assertThat(result.items().getFirst().manuallyPrioritised()).isTrue();
    }

    @Test
    void targetAndCapacityAreHandledAsSeparateConcepts() {
        PlanningResult result = plan(settings(10, 4), item("urgent", 6, "08:00", "12:00", Duration.ofMinutes(60)));

        assertThat(result.accountingTotals().plannedTentative()).isEqualTo(6);
        assertThat(result.accountingTotals().capacityOverflow()).isZero();
        assertThat(result.items().getFirst().priorityExplanation()).contains("acima do objetivo");
    }

    @Test
    void targetAboveCapacityCreatesWarning() {
        PlanningResult result = plan(settings(8, 10), item("stretch", 2, "08:00", "18:00"));

        assertThat(result.warning()).contains("stretch target");
    }

    @Test
    void urgentWorkCanExceedTargetButNotCapacityWithoutOverflow() {
        PlanningResult result = plan(settings(8, 3), item("urgent", 5, "08:00", "12:00", Duration.ofMinutes(60)));

        assertThat(result.capacityUsed()).isEqualTo(5);
        assertThat(result.accountingTotals().capacityOverflow()).isZero();
    }

    @Test
    void atomicCustomerBatchesAreNotPartiallyPlannedByAccident() {
        PlanningResult result = plan(settings(5, 5), item("too-large", 8, "08:00", "18:00", Duration.ofMinutes(60)));

        assertThat(result.items().getFirst().riskClassification()).isEqualTo(RiskClassification.OVER_CAPACITY);
        assertThat(result.accountingTotals().capacityOverflow()).isEqualTo(8);
        assertThat(result.capacityUsed()).isZero();
    }

    @Test
    void readyWheelsRemainVisibleWithoutConsumingProductionCapacity() {
        PlanningWorkItem ready = item("ready", 6, "08:00", "18:00", Duration.ZERO,
                at(day, "08:00"), LifecycleStatus.READY_FOR_PICKUP, null, false, null, null);

        PlanningResult result = plan(settings(5, 5), ready);

        assertThat(result.items().getFirst().riskClassification()).isEqualTo(RiskClassification.ON_TRACK);
        assertThat(result.accountingTotals().plannedConfirmed()).isEqualTo(6);
        assertThat(result.capacityUsed()).isZero();
    }

    @Test
    void everyOpenWheelSatisfiesTheAccountingInvariant() {
        PlanningResult result = plan(settings(10, 8),
                item("planned", 2, "08:00", "18:00"),
                item("future", 3, "21:00", "09:00", day.plusDays(1), Duration.ofMinutes(60)),
                item("overflow", 20, "08:00", "18:00", Duration.ofMinutes(60))
        );

        assertThat(result.totalKnownWheels()).isEqualTo(result.accountingTotals().accountedWheels());
    }

    @Test
    void resultsAreDeterministicForEqualInputs() {
        PlanningWorkItem a = item("a", 2, "08:00", "18:00");
        PlanningWorkItem b = item("b", 2, "08:00", "18:00");

        PlanningResult first = plan(a, b);
        PlanningResult second = plan(a, b);

        assertThat(first.items()).extracting(PlanningItemResult::requestId)
                .containsExactlyElementsOf(second.items().stream().map(PlanningItemResult::requestId).toList());
    }

    @Test
    void europeLisbonTimezoneAndDaylightSavingChangesAreHandled() {
        LocalDate dstDay = LocalDate.of(2026, 3, 29);
        OffsetDateTime current = dstDay.atTime(7, 0).atZone(lisbon).toOffsetDateTime();
        PlanningWorkItem item = item("dst", 2,
                dstDay.atTime(8, 0).atZone(lisbon).toOffsetDateTime(),
                dstDay.atTime(18, 0).atZone(lisbon).toOffsetDateTime(),
                Duration.ofMinutes(60));

        PlanningResult result = engine.generate(dstDay, current, settings(10, 8), List.of(item), lisbon);

        assertThat(result.items().getFirst().assignedProductionDate()).isEqualTo(dstDay);
        assertThat(result.totalKnownWheels()).isEqualTo(result.accountingTotals().accountedWheels());
    }

    private PlanningResult plan(PlanningWorkItem... items) {
        return plan(settings(40, 32), items);
    }

    private PlanningResult plan(PlanningSettings settings, PlanningWorkItem... items) {
        return engine.generate(day, now, settings, List.of(items), lisbon);
    }

    private PlanningSettings settings(int capacity, int target) {
        return new PlanningSettings(capacity, target, 20, List.of(
                new PlanningTimeWindow("Fim da manhã", LocalTime.of(12, 30), 1),
                new PlanningTimeWindow("Meio da tarde", LocalTime.of(15, 30), 2),
                new PlanningTimeWindow("Fim do dia", LocalTime.of(18, 30), 3)
        ));
    }

    private PlanningWorkItem item(String key, int quantity, String dropEnd, String pickupStart) {
        return item(key, quantity, dropEnd, pickupStart, Duration.ofMinutes(quantity * 20L));
    }

    private PlanningWorkItem item(String key, int quantity, String dropEnd, String pickupStart, Duration estimate) {
        return item(key, quantity, at(day, dropEnd), at(day, pickupStart), estimate);
    }

    private PlanningWorkItem item(String key, int quantity, String dropEnd, String pickupStart, LocalDate pickupDate, Duration estimate) {
        return item(key, quantity, at(day, dropEnd), at(pickupDate, pickupStart), estimate);
    }

    private PlanningWorkItem item(String key, int quantity, OffsetDateTime dropEnd, OffsetDateTime pickupStart, Duration estimate) {
        return new PlanningWorkItem(
                id(key),
                "Cliente " + key,
                "Motorista",
                quantity,
                dropEnd.minusHours(3),
                dropEnd.minusHours(1),
                dropEnd,
                null,
                pickupStart,
                LifecycleStatus.REGISTERED,
                null,
                false,
                null,
                null,
                estimate,
                true
        );
    }

    private PlanningWorkItem item(String key, int quantity, String dropEnd, String pickupStart, Duration estimate,
                                  OffsetDateTime actualArrival, LifecycleStatus status, Integer manualPriority,
                                  boolean locked, LocalDate lockedDate, String lockedWindow) {
        return item(key, quantity, at(day, dropEnd).minusHours(1).toLocalTime().toString().substring(0, 5), dropEnd,
                at(day, pickupStart), estimate, actualArrival, status, manualPriority, locked, lockedDate, lockedWindow);
    }

    private PlanningWorkItem item(String key, int quantity, String dropStart, String dropEnd, OffsetDateTime pickupStart,
                                  Duration estimate, OffsetDateTime actualArrival, LifecycleStatus status,
                                  Integer manualPriority, boolean locked, LocalDate lockedDate, String lockedWindow) {
        return new PlanningWorkItem(
                id(key),
                "Cliente " + key,
                "Motorista",
                quantity,
                now.minusHours(2),
                at(day, dropStart),
                at(day, dropEnd),
                actualArrival,
                pickupStart,
                status,
                manualPriority,
                locked,
                lockedDate,
                lockedWindow,
                estimate,
                true
        );
    }

    private OffsetDateTime at(LocalDate date, String hhmm) {
        return date.atTime(LocalTime.parse(hhmm)).atZone(lisbon).toOffsetDateTime();
    }

    private UUID id(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
