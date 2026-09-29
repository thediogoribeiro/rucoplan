package pt.rucodel.productionplanning.service;

import org.junit.jupiter.api.Test;
import pt.rucodel.productionplanning.domain.GenerationTrigger;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.entity.ProductionSiteEntity;
import pt.rucodel.productionplanning.exception.InvalidRequestException;

import java.time.*;
import java.util.List;

import static org.mockito.Mockito.*;

class PlanningSchedulerTest {
    @Test
    void startupRecoveryAfterSevenCreatesMissingCurrentDayPlan() {
        MultiDayProductionPlanningService service = mock(MultiDayProductionPlanningService.class);
        ProductionSiteService productionSites = mock(ProductionSiteService.class);
        ProductionSiteEntity portugal = new ProductionSiteEntity();
        portugal.setCode(ProductionSiteCode.PT);
        portugal.setDisplayName("Portugal");
        portugal.setTimezone("Europe/Lisbon");
        portugal.setActive(true);
        when(productionSites.activeSites()).thenReturn(List.of(portugal));
        Clock clock = Clock.fixed(LocalDate.of(2026, 9, 2).atTime(8, 0).atZone(ZoneId.of("Europe/Lisbon")).toInstant(),
                ZoneId.of("Europe/Lisbon"));
        PlanningScheduler scheduler = new PlanningScheduler(service, productionSites, clock);

        scheduler.recoverMissedCurrentDayPlan();

        verify(service).recalculate(ProductionSiteCode.PT, LocalDate.of(2026, 9, 2), GenerationTrigger.STARTUP_RECOVERY, "SYSTEM");
    }

    @Test
    void missingTargetsInOneSiteDoNotStopOtherSites() {
        MultiDayProductionPlanningService service = mock(MultiDayProductionPlanningService.class);
        ProductionSiteService productionSites = mock(ProductionSiteService.class);
        ProductionSiteEntity luxembourg = site(ProductionSiteCode.LUX, "Luxemburgo", "Europe/Luxembourg");
        ProductionSiteEntity portugal = site(ProductionSiteCode.PT, "Portugal", "Europe/Lisbon");
        when(productionSites.activeSites()).thenReturn(List.of(luxembourg, portugal));
        Clock clock = Clock.fixed(LocalDate.of(2026, 9, 2).atTime(8, 0).atZone(ZoneId.of("Europe/Lisbon")).toInstant(),
                ZoneId.of("Europe/Lisbon"));
        doThrow(new InvalidRequestException("TARGET_CONFIGURATION_MISSING", "Configure os targets."))
                .when(service).recalculate(ProductionSiteCode.LUX, LocalDate.of(2026, 9, 2), GenerationTrigger.STARTUP_RECOVERY, "SYSTEM");
        PlanningScheduler scheduler = new PlanningScheduler(service, productionSites, clock);

        scheduler.recoverMissedCurrentDayPlan();

        verify(service).recalculate(ProductionSiteCode.LUX, LocalDate.of(2026, 9, 2), GenerationTrigger.STARTUP_RECOVERY, "SYSTEM");
        verify(service).recalculate(ProductionSiteCode.PT, LocalDate.of(2026, 9, 2), GenerationTrigger.STARTUP_RECOVERY, "SYSTEM");
    }

    private ProductionSiteEntity site(ProductionSiteCode code, String name, String timezone) {
        ProductionSiteEntity site = new ProductionSiteEntity();
        site.setCode(code);
        site.setDisplayName(name);
        site.setTimezone(timezone);
        site.setActive(true);
        return site;
    }
}
