package pt.rucodel.productionplanning.domain;

public enum GenerationTrigger {
    SCHEDULED,
    STARTUP_RECOVERY,
    MANUAL,
    AUTOMATIC_RECALCULATION
}
