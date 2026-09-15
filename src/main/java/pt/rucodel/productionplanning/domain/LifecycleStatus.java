package pt.rucodel.productionplanning.domain;

public enum LifecycleStatus {
    REGISTERED,
    ARRIVED_AT_FACTORY,
    IN_PRODUCTION,
    READY_FOR_PICKUP,
    PICKED_UP_FROM_FACTORY,
    CANCELLED
}
