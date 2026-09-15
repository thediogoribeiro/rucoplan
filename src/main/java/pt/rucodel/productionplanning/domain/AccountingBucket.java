package pt.rucodel.productionplanning.domain;

public enum AccountingBucket {
    PLANNED_CONFIRMED,
    PLANNED_TENTATIVE,
    WAITING_FOR_ARRIVAL,
    FUTURE_WORKLOAD,
    AT_RISK,
    CAPACITY_OVERFLOW
}
