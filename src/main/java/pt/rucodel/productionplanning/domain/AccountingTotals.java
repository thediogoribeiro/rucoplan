package pt.rucodel.productionplanning.domain;

public record AccountingTotals(
        int plannedConfirmed,
        int plannedTentative,
        int waitingForArrival,
        int futureWorkload,
        int atRisk,
        int capacityOverflow
) {
    public int accountedWheels() {
        return plannedConfirmed + plannedTentative + waitingForArrival + futureWorkload + atRisk + capacityOverflow;
    }

    public int plannedWheels() {
        return plannedConfirmed + plannedTentative;
    }
}
