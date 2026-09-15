package pt.rucodel.productionplanning.integration;

import java.time.Duration;
import java.util.Optional;

public interface ProductionDataPort {
    Optional<Duration> historicalDurationEstimate(int wheelQuantity);

    Optional<ProductionJobSnapshot> findJobByCorrelationId(String correlationId);

    boolean integrationEnabled();
}
