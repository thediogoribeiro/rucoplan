package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.repository.ProductionPlanRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class RecalculationService {
    private final ProductionPlanRepository plans;
    private final Clock clock;
    private final ZoneId businessZone;

    public RecalculationService(ProductionPlanRepository plans, Clock clock, AppProperties appProperties) {
        this.plans = plans;
        this.clock = clock;
        this.businessZone = ZoneId.of(appProperties.timezone());
    }

    @Transactional
    public void markCurrentAndFuturePlans() {
        plans.markCurrentPlansForRecalculation(LocalDate.now(clock.withZone(businessZone)));
    }
}
