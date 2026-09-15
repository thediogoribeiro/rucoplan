package pt.rucodel.productionplanning.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record AdminDashboardResponse(
        LocalDate date,
        int dailyTarget,
        int dailyCapacity,
        int wheelsPlanned,
        int wheelsAtFactory,
        int wheelsExpectedToday,
        int wheelsInProduction,
        int wheelsReady,
        int wheelsAtRisk,
        int capacityOverflow,
        int remainingAvailableCapacity,
        OffsetDateTime lastPlanGeneratedAt,
        PlanResponse plan,
        Map<String, List<PlanItemResponse>> planByReadyWindow,
        List<RequestResponse> expectedFactoryArrivals,
        List<String> risksAndExceptions,
        List<RequestResponse> futureWorkload
) {
}
