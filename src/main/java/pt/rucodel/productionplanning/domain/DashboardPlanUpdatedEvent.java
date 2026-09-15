package pt.rucodel.productionplanning.domain;

import java.time.LocalDate;

public record DashboardPlanUpdatedEvent(LocalDate planningDate) {
}
