package pt.rucodel.productionplanning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DailySettingsRequest(
        @NotNull @Min(0) Integer dailyCapacity,
        @NotNull @Min(0) Integer dailyTarget,
        @NotNull @Min(1) Integer fallbackMinutesPerWheel,
        @Valid List<TimeWindowDto> timeWindows,
        Long version
) {
}
