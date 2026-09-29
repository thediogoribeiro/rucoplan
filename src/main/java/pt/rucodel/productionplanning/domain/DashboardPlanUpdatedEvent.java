package pt.rucodel.productionplanning.domain;

import java.time.LocalDate;

public record DashboardPlanUpdatedEvent(ProductionSiteCode productionSite, LocalDate planningDate) {
}
