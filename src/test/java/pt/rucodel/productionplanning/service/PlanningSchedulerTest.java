package pt.rucodel.productionplanning.service;

import org.junit.jupiter.api.Test;
import pt.rucodel.productionplanning.domain.GenerationTrigger;

import java.time.*;

import static org.mockito.Mockito.*;

class PlanningSchedulerTest {
    @Test
    void startupRecoveryAfterSevenCreatesMissingCurrentDayPlan() {
        MultiDayProductionPlanningService service = mock(MultiDayProductionPlanningService.class);
        AppProperties properties = new AppProperties("Rucodel Production Planning", "Europe/Lisbon");
        Clock clock = Clock.fixed(LocalDate.of(2026, 9, 2).atTime(8, 0).atZone(ZoneId.of("Europe/Lisbon")).toInstant(),
                ZoneId.of("Europe/Lisbon"));
        PlanningScheduler scheduler = new PlanningScheduler(service, clock, properties);

        scheduler.recoverMissedCurrentDayPlan();

        verify(service).recalculate(LocalDate.of(2026, 9, 2), GenerationTrigger.STARTUP_RECOVERY, "SYSTEM");
    }
}
