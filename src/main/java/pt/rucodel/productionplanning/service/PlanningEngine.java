package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import pt.rucodel.productionplanning.domain.*;
import pt.rucodel.productionplanning.exception.InvalidRequestException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PlanningEngine {
    public PlanningResult generate(LocalDate planningDate, OffsetDateTime currentTime, PlanningSettings settings,
                                   List<PlanningWorkItem> workItems, ZoneId businessZone) {
        if (settings.timeWindows() == null || settings.timeWindows().isEmpty()) {
            throw new InvalidRequestException("At least one production time window is required to generate a plan.");
        }
        List<PlanningTimeWindow> windows = settings.timeWindows().stream()
                .sorted(Comparator.comparingInt(PlanningTimeWindow::sortOrder))
                .toList();
        validateWindows(windows);

        List<PlanningWorkItem> sorted = workItems.stream()
                .sorted(priorityComparator(currentTime))
                .toList();
        List<PlanningItemResult> items = new ArrayList<>();
        int capacityUsed = 0;
        int sequence = 1;
        for (PlanningWorkItem workItem : sorted) {
            Allocation allocation = allocate(workItem, planningDate, currentTime, settings, windows, businessZone, capacityUsed);
            if (allocation.consumesCapacity()) {
                capacityUsed += workItem.quantity();
            }
            items.add(toResult(workItem, allocation, sequence++));
        }

        AccountingTotals totals = accountingTotals(items);
        int totalKnown = workItems.stream().mapToInt(PlanningWorkItem::quantity).sum();
        if (totalKnown != totals.accountedWheels()) {
            throw new IllegalStateException("Planning accounting invariant failed: known=" + totalKnown
                    + " accounted=" + totals.accountedWheels());
        }
        String warning = settings.dailyTarget() > settings.dailyCapacity()
                ? "Daily target is above configured capacity. This is a stretch target."
                : null;
        return new PlanningResult(
                planningDate,
                currentTime,
                capacityUsed,
                settings.dailyTarget(),
                totalKnown,
                totals,
                workItems.stream().anyMatch(PlanningWorkItem::fallbackEstimateUsed),
                warning,
                items
        );
    }

    private Allocation allocate(PlanningWorkItem item, LocalDate planningDate, OffsetDateTime currentTime,
                                PlanningSettings settings, List<PlanningTimeWindow> windows, ZoneId zone,
                                int capacityUsed) {
        if (hasMissingInformation(item)) {
            return new Allocation(null, null, AvailabilityClassification.WAITING_FOR_ARRIVAL,
                    RiskClassification.MISSING_INFORMATION, AccountingBucket.AT_RISK,
                    "Informação em falta: o pedido permanece visível no planeamento.", false);
        }

        OffsetDateTime availabilityAt = item.availabilityAt();
        OffsetDateTime dueAt = item.requestedFactoryPickupWindowStart();
        AvailabilityClassification availability = availabilityClassification(item, currentTime);
        Duration slack = slack(item, currentTime);
        boolean dueAlreadyPassed = dueAt.isBefore(currentTime) || dueAt.isEqual(currentTime);

        if (item.planningLocked() && item.lockedAssignedProductionDate() != null) {
            RiskClassification risk = lockedRisk(item, currentTime, zone);
            boolean sameDay = item.lockedAssignedProductionDate().equals(planningDate);
            boolean capacityExceeded = sameDay && capacityUsed + item.quantity() > settings.dailyCapacity();
            if (capacityExceeded) {
                return new Allocation(item.lockedAssignedProductionDate(), item.lockedAssignedWindowLabel(), availability,
                        RiskClassification.OVER_CAPACITY, AccountingBucket.CAPACITY_OVERFLOW,
                        "Bloqueado: decisão manual preservada, mas excede a capacidade diária.", false);
            }
            return new Allocation(item.lockedAssignedProductionDate(), item.lockedAssignedWindowLabel(), availability,
                    risk, bucketForLocked(risk, availability, sameDay, item), sameDay,
                    "Bloqueado: decisão manual preservada na regeneração.");
        }

        ProductionWindowAssignment compatibleWindow = compatibleWindow(item, planningDate, currentTime, windows, zone);
        if (compatibleWindow == null) {
            OffsetDateTime lastCutoff = planningDate.atTime(windows.getLast().cutoffTime()).atZone(zone).toOffsetDateTime();
            LocalDate localDue = dueAt.atZoneSameInstant(zone).toLocalDate();
            LocalDate localAvailability = availabilityAt.atZoneSameInstant(zone).toLocalDate();
            AccountingBucket bucket = availabilityAt.toLocalDate().isAfter(planningDate)
                    || localAvailability.isAfter(planningDate)
                    || availabilityAt.isAfter(lastCutoff)
                    || localDue.isAfter(planningDate)
                    ? AccountingBucket.FUTURE_WORKLOAD
                    : AccountingBucket.WAITING_FOR_ARRIVAL;
            RiskClassification risk = dueAlreadyPassed ? RiskClassification.OVERDUE : RiskClassification.ON_TRACK;
            if (slack.isNegative() && !dueAlreadyPassed) {
                risk = RiskClassification.AT_RISK;
                bucket = AccountingBucket.AT_RISK;
            }
            return new Allocation(null, null, availability, risk, bucket,
                    explainNotAllocated(item, planningDate, zone), false);
        }

        boolean consumesCapacity = item.lifecycleStatus() != LifecycleStatus.READY_FOR_PICKUP;
        if (consumesCapacity && capacityUsed + item.quantity() > settings.dailyCapacity()) {
            return new Allocation(null, "Excesso de capacidade", availability, RiskClassification.OVER_CAPACITY,
                    AccountingBucket.CAPACITY_OVERFLOW, "Excesso: capacidade diária esgotada.", false);
        }

        RiskClassification risk = dueAlreadyPassed
                ? RiskClassification.OVERDUE
                : compatibleWindow.readyAt().isAfter(dueAt) || slack.isNegative()
                ? RiskClassification.AT_RISK
                : RiskClassification.ON_TRACK;
        String explanation = explainAllocated(item, compatibleWindow, settings, capacityUsed, risk);
        return new Allocation(planningDate, compatibleWindow.window().label(), availability, risk,
                bucketForAssigned(risk, availability), explanation, consumesCapacity);
    }

    private PlanningItemResult toResult(PlanningWorkItem item, Allocation allocation, int sequence) {
        return new PlanningItemResult(
                item.requestId(),
                item.customerName(),
                item.driverName(),
                item.quantity(),
                item.availabilityAt(),
                item.requestedFactoryPickupWindowStart(),
                allocation.assignedProductionDate(),
                allocation.assignedWindowLabel(),
                allocation.availabilityClassification(),
                allocation.riskClassification(),
                BigDecimal.valueOf(sequence),
                priorityPrefix(item) + allocation.explanation(),
                item.manualPriority() != null,
                item.planningLocked(),
                allocation.accountingBucket()
        );
    }

    private Comparator<PlanningWorkItem> priorityComparator(OffsetDateTime currentTime) {
        return Comparator
                .comparing((PlanningWorkItem item) -> item.manualPriority() == null)
                .thenComparing(item -> item.manualPriority() == null ? Integer.MAX_VALUE : item.manualPriority())
                .thenComparing((PlanningWorkItem item) -> !isAlreadyOverdue(item, currentTime))
                .thenComparing(item -> slack(item, currentTime))
                .thenComparing(PlanningWorkItem::requestedFactoryPickupWindowStart, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing((PlanningWorkItem item) -> !item.physicallyAtFactory())
                .thenComparing(PlanningWorkItem::registeredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PlanningWorkItem::requestId);
    }

    private boolean isAlreadyOverdue(PlanningWorkItem item, OffsetDateTime currentTime) {
        return item.requestedFactoryPickupWindowStart() != null
                && (item.requestedFactoryPickupWindowStart().isBefore(currentTime)
                || item.requestedFactoryPickupWindowStart().isEqual(currentTime));
    }

    private Duration slack(PlanningWorkItem item, OffsetDateTime currentTime) {
        if (item.requestedFactoryPickupWindowStart() == null || item.availabilityAt() == null || item.estimatedProductionDuration() == null) {
            return Duration.ofDays(3650);
        }
        OffsetDateTime base = item.availabilityAt().isAfter(currentTime) ? item.availabilityAt() : currentTime;
        return Duration.between(base, item.requestedFactoryPickupWindowStart()).minus(item.estimatedProductionDuration());
    }

    private ProductionWindowAssignment compatibleWindow(PlanningWorkItem item, LocalDate planningDate,
                                                       OffsetDateTime currentTime,
                                                       List<PlanningTimeWindow> windows, ZoneId zone) {
        OffsetDateTime availabilityAt = item.availabilityAt();
        OffsetDateTime base = latest(startOfDay(planningDate, zone), availabilityAt);
        if (currentTime.atZoneSameInstant(zone).toLocalDate().equals(planningDate)) {
            base = latest(base, currentTime);
        }
        OffsetDateTime earliestReady = base.plus(item.estimatedProductionDuration());
        if (!earliestReady.atZoneSameInstant(zone).toLocalDate().equals(planningDate)) {
            return null;
        }
        for (PlanningTimeWindow window : windows) {
            OffsetDateTime cutoff = planningDate.atTime(window.cutoffTime()).atZone(zone).toOffsetDateTime();
            if (!cutoff.isBefore(earliestReady)) {
                return new ProductionWindowAssignment(window, earliestReady);
            }
        }
        return null;
    }

    private OffsetDateTime startOfDay(LocalDate planningDate, ZoneId zone) {
        return planningDate.atStartOfDay(zone).toOffsetDateTime();
    }

    private OffsetDateTime latest(OffsetDateTime first, OffsetDateTime second) {
        return first.isAfter(second) ? first : second;
    }

    private AvailabilityClassification availabilityClassification(PlanningWorkItem item, OffsetDateTime currentTime) {
        if (item.physicallyAtFactory()) {
            return AvailabilityClassification.CONFIRMED;
        }
        OffsetDateTime availabilityAt = item.availabilityAt();
        if (availabilityAt == null || !availabilityAt.isAfter(currentTime)) {
            return AvailabilityClassification.WAITING_FOR_ARRIVAL;
        }
        return AvailabilityClassification.TENTATIVE;
    }

    private RiskClassification lockedRisk(PlanningWorkItem item, OffsetDateTime currentTime, ZoneId zone) {
        if (isAlreadyOverdue(item, currentTime)) {
            return RiskClassification.OVERDUE;
        }
        if (item.lockedAssignedProductionDate() == null || item.lockedAssignedWindowLabel() == null) {
            return RiskClassification.AT_RISK;
        }
        if (slack(item, currentTime).isNegative()) {
            return RiskClassification.AT_RISK;
        }
        return RiskClassification.ON_TRACK;
    }

    private AccountingBucket bucketForAssigned(RiskClassification risk, AvailabilityClassification availability) {
        if (risk == RiskClassification.OVER_CAPACITY) {
            return AccountingBucket.CAPACITY_OVERFLOW;
        }
        if (risk == RiskClassification.AT_RISK || risk == RiskClassification.OVERDUE || risk == RiskClassification.MISSING_INFORMATION) {
            return AccountingBucket.AT_RISK;
        }
        return availability == AvailabilityClassification.CONFIRMED
                ? AccountingBucket.PLANNED_CONFIRMED
                : AccountingBucket.PLANNED_TENTATIVE;
    }

    private AccountingBucket bucketForLocked(RiskClassification risk, AvailabilityClassification availability,
                                             boolean sameDay, PlanningWorkItem item) {
        if (!sameDay) {
            return AccountingBucket.FUTURE_WORKLOAD;
        }
        return bucketForAssigned(risk, availability);
    }

    private AccountingTotals accountingTotals(List<PlanningItemResult> items) {
        int plannedConfirmed = sum(items, AccountingBucket.PLANNED_CONFIRMED);
        int plannedTentative = sum(items, AccountingBucket.PLANNED_TENTATIVE);
        int waiting = sum(items, AccountingBucket.WAITING_FOR_ARRIVAL);
        int future = sum(items, AccountingBucket.FUTURE_WORKLOAD);
        int atRisk = sum(items, AccountingBucket.AT_RISK);
        int overflow = sum(items, AccountingBucket.CAPACITY_OVERFLOW);
        return new AccountingTotals(plannedConfirmed, plannedTentative, waiting, future, atRisk, overflow);
    }

    private int sum(List<PlanningItemResult> items, AccountingBucket bucket) {
        return items.stream()
                .filter(item -> item.accountingBucket() == bucket)
                .mapToInt(PlanningItemResult::quantity)
                .sum();
    }

    private boolean hasMissingInformation(PlanningWorkItem item) {
        return item.quantity() <= 0
                || item.expectedFactoryDropOffWindowEnd() == null
                || item.requestedFactoryPickupWindowStart() == null
                || item.estimatedProductionDuration() == null;
    }

    private String priorityPrefix(PlanningWorkItem item) {
        if (item.manualPriority() != null) {
            return "Prioridade manual " + item.manualPriority() + ": ";
        }
        return "Prioridade calculada: ";
    }

    private String explainAllocated(PlanningWorkItem item, ProductionWindowAssignment assignment,
                                    PlanningSettings settings, int capacityUsed, RiskClassification risk) {
        List<String> parts = new ArrayList<>();
        parts.add("levantamento pedido às " + item.requestedFactoryPickupWindowStart().toLocalTime() + ".");
        if (item.physicallyAtFactory()) {
            parts.add("Confirmado: jantes fisicamente na fábrica.");
        } else {
            parts.add("Tentativo: esperado na fábrica entre "
                    + item.expectedFactoryDropOffWindowStart().toLocalTime() + " e "
                    + item.expectedFactoryDropOffWindowEnd().toLocalTime() + ".");
        }
        if (risk == RiskClassification.AT_RISK) {
            parts.add("Em risco: a conclusão estimada ultrapassa ou aperta a janela de levantamento.");
        } else if (risk == RiskClassification.OVERDUE) {
            parts.add("Atrasado: o início da janela de levantamento já passou.");
        }
        if (capacityUsed + item.quantity() > settings.dailyTarget() && capacityUsed + item.quantity() <= settings.dailyCapacity()) {
            parts.add("Planeado acima do objetivo porque o prazo usa capacidade disponível.");
        }
        parts.add("Janela atribuída: " + assignment.window().label() + ".");
        return String.join(" ", parts);
    }

    private String explainNotAllocated(PlanningWorkItem item, LocalDate planningDate, ZoneId zone) {
        OffsetDateTime availabilityAt = item.availabilityAt();
        if (availabilityAt == null) {
            return "Sem hora de disponibilidade na fábrica; mantido para revisão.";
        }
        LocalDate localAvailability = availabilityAt.atZoneSameInstant(zone).toLocalDate();
        if (localAvailability.isAfter(planningDate)) {
            return "Carga futura: chegada prevista depois da data selecionada.";
        }
        return "A aguardar chegada ou sem janela de produção compatível nesta data.";
    }

    private void validateWindows(List<PlanningTimeWindow> windows) {
        for (int i = 1; i < windows.size(); i++) {
            if (!windows.get(i).cutoffTime().isAfter(windows.get(i - 1).cutoffTime())) {
                throw new InvalidRequestException("Production time-window cut-offs must be strictly increasing.");
            }
        }
    }

    private record ProductionWindowAssignment(PlanningTimeWindow window, OffsetDateTime readyAt) {
    }

    private record Allocation(LocalDate assignedProductionDate, String assignedWindowLabel,
                              AvailabilityClassification availabilityClassification, RiskClassification riskClassification,
                              AccountingBucket accountingBucket, String explanation, boolean consumesCapacity) {
        Allocation(LocalDate assignedProductionDate, String assignedWindowLabel,
                   AvailabilityClassification availabilityClassification, RiskClassification riskClassification,
                   AccountingBucket accountingBucket, String explanation, boolean consumesCapacity) {
            this.assignedProductionDate = assignedProductionDate;
            this.assignedWindowLabel = assignedWindowLabel;
            this.availabilityClassification = availabilityClassification;
            this.riskClassification = riskClassification;
            this.accountingBucket = accountingBucket;
            this.explanation = explanation;
            this.consumesCapacity = consumesCapacity;
        }

        Allocation(LocalDate assignedProductionDate, String assignedWindowLabel,
                   AvailabilityClassification availabilityClassification, RiskClassification riskClassification,
                   AccountingBucket accountingBucket, boolean consumesCapacity, String explanation) {
            this(assignedProductionDate, assignedWindowLabel, availabilityClassification, riskClassification,
                    accountingBucket, explanation, consumesCapacity);
        }
    }
}
