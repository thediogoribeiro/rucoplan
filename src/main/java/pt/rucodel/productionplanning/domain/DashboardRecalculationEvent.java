package pt.rucodel.productionplanning.domain;

import java.time.LocalDate;

public record DashboardRecalculationEvent(
        ProductionSiteCode productionSite,
        LocalDate planningDate,
        RecalculationStatus status,
        String reason,
        String correlationId,
        String errorCode
) {
}
