package pt.rucodel.productionplanning.domain;

import java.time.LocalTime;

public record PlanningTimeWindow(
        String label,
        LocalTime cutoffTime,
        int sortOrder
) {
}
