package pt.rucodel.productionplanning.service;

import org.springframework.context.ApplicationEventPublisher;
import pt.rucodel.productionplanning.domain.DashboardRecalculationEvent;
import org.springframework.stereotype.Component;
import pt.rucodel.productionplanning.domain.DashboardPlanUpdatedEvent;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.domain.RecalculationStatus;

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

    public void publishRecalculationStatus(ProductionSiteCode siteCode, LocalDate date,
                                           RecalculationStatus status, String reason,
                                           String correlationId, String errorCode) {
        publisher.publishEvent(new DashboardRecalculationEvent(siteCode, date, status, reason, correlationId, errorCode));
    }
}
