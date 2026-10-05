package pt.rucodel.productionplanning.service;

import org.junit.jupiter.api.Test;
import pt.rucodel.productionplanning.domain.GenerationTrigger;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.domain.RecalculationStatus;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AutomaticPlanningRecalculationServiceTest {
    @Test
    void coalescesConcurrentRecalculationsForTheSameSiteWithoutDroppingFinalUpdate() throws Exception {
        MultiDayProductionPlanningService planning = mock(MultiDayProductionPlanningService.class);
        DashboardEventPublisher events = mock(DashboardEventPublisher.class);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        doAnswer(invocation -> {
            firstStarted.countDown();
            releaseFirst.await(2, TimeUnit.SECONDS);
            return null;
        }).doReturn(null)
                .when(planning).recalculate(eq(ProductionSiteCode.PT), any(LocalDate.class),
                        eq(GenerationTrigger.AUTOMATIC_RECALCULATION), eq("SYSTEM"));
        AutomaticPlanningRecalculationService service = new AutomaticPlanningRecalculationService(
                planning, events, Executors.newFixedThreadPool(2));

        service.schedule(ProductionSiteCode.PT, LocalDate.of(2026, 9, 3), "communicated");
        firstStarted.await(2, TimeUnit.SECONDS);
        service.schedule(ProductionSiteCode.PT, LocalDate.of(2026, 9, 2), "arrival");
        releaseFirst.countDown();

        verify(planning, timeout(3000).times(2))
                .recalculate(eq(ProductionSiteCode.PT), any(LocalDate.class),
                        eq(GenerationTrigger.AUTOMATIC_RECALCULATION), eq("SYSTEM"));
        verify(planning).recalculate(ProductionSiteCode.PT, LocalDate.of(2026, 9, 3),
                GenerationTrigger.AUTOMATIC_RECALCULATION, "SYSTEM");
        verify(planning).recalculate(ProductionSiteCode.PT, LocalDate.of(2026, 9, 2),
                GenerationTrigger.AUTOMATIC_RECALCULATION, "SYSTEM");
        verify(events, atLeastOnce()).publishRecalculationStatus(eq(ProductionSiteCode.PT), any(LocalDate.class),
                eq(RecalculationStatus.STARTED), anyString(), anyString(), isNull());
        verify(events, atLeastOnce()).publishRecalculationStatus(eq(ProductionSiteCode.PT), any(LocalDate.class),
                eq(RecalculationStatus.COMPLETED), anyString(), anyString(), isNull());
        service.shutdown();
    }

    @Test
    void keepsProductionSitesIndependent() {
        MultiDayProductionPlanningService planning = mock(MultiDayProductionPlanningService.class);
        DashboardEventPublisher events = mock(DashboardEventPublisher.class);
        AutomaticPlanningRecalculationService service = new AutomaticPlanningRecalculationService(
                planning, events, Executors.newFixedThreadPool(2));

        service.schedule(ProductionSiteCode.PT, LocalDate.of(2026, 9, 2), "communicated");
        service.schedule(ProductionSiteCode.LUX, LocalDate.of(2026, 9, 2), "communicated");

        verify(planning, timeout(3000)).recalculate(ProductionSiteCode.PT, LocalDate.of(2026, 9, 2),
                GenerationTrigger.AUTOMATIC_RECALCULATION, "SYSTEM");
        verify(planning, timeout(3000)).recalculate(ProductionSiteCode.LUX, LocalDate.of(2026, 9, 2),
                GenerationTrigger.AUTOMATIC_RECALCULATION, "SYSTEM");
        service.shutdown();
    }
}
