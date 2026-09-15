package pt.rucodel.productionplanning.integration;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class DisabledProductionDataAdapter implements ProductionDataPort {
    @Override
    public Optional<Duration> historicalDurationEstimate(int wheelQuantity) {
        return Optional.empty();
    }

    @Override
    public Optional<ProductionJobSnapshot> findJobByCorrelationId(String correlationId) {
        return Optional.empty();
    }

    @Override
    public boolean integrationEnabled() {
        return false;
    }
}
