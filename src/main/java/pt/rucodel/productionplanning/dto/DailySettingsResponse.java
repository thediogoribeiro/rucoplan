package pt.rucodel.productionplanning.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DailySettingsResponse(
        UUID id,
        String settingsKey,
        LocalDate settingsDate,
        int dailyCapacity,
        int dailyTarget,
        int fallbackMinutesPerWheel,
        boolean targetAboveCapacity,
        List<TimeWindowDto> timeWindows,
        long version
) {
}
