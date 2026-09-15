package pt.rucodel.productionplanning.domain;

import java.util.List;

public record PlanningSettings(
        int dailyCapacity,
        int dailyTarget,
        int fallbackMinutesPerWheel,
        List<PlanningTimeWindow> timeWindows
) {
}
