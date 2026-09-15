package pt.rucodel.productionplanning.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import pt.rucodel.productionplanning.domain.DashboardPlanUpdatedEvent;

import java.time.LocalDate;

@Component
public class DashboardEventPublisher {
    private final ApplicationEventPublisher publisher;

    public DashboardEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishPlanUpdated(LocalDate date) {
        publisher.publishEvent(new DashboardPlanUpdatedEvent(date));
    }
}
