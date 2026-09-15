package pt.rucodel.productionplanning.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PlanningResult(
        LocalDate planningDate,
        OffsetDateTime generatedAt,
        int capacityUsed,
        int targetUsed,
        int totalKnownWheels,
        AccountingTotals accountingTotals,
        boolean fallbackEstimatesUsed,
        String warning,
        List<PlanningItemResult> items
) {
}
