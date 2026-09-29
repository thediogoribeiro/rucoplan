package pt.rucodel.productionplanning.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import pt.rucodel.productionplanning.domain.DashboardPlanUpdatedEvent;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;

import java.time.LocalDate;

@Component
public class DashboardEventPublisher {
    private final ApplicationEventPublisher publisher;

    public DashboardEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishPlanUpdated(LocalDate date) {
        publishPlanUpdated(ProductionSiteCode.PT, date);
    }

    public void publishPlanUpdated(ProductionSiteCode siteCode, LocalDate date) {
        publisher.publishEvent(new DashboardPlanUpdatedEvent(siteCode, date));
    }
}
