package pt.rucodel.productionplanning.integration;

public record ProductionJobSnapshot(
        String jobId,
        String correlationId,
        String currentStage,
        int completedQuantity
) {
}
