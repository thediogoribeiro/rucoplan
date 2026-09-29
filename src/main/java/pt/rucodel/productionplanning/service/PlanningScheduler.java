package pt.rucodel.productionplanning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.rucodel.productionplanning.domain.GenerationTrigger;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.entity.ProductionSiteEntity;
import pt.rucodel.productionplanning.exception.InvalidRequestException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class PlanningScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlanningScheduler.class);

    private final MultiDayProductionPlanningService productionPlanningService;
    private final ProductionSiteService productionSites;
    private final Clock clock;

    public PlanningScheduler(MultiDayProductionPlanningService productionPlanningService,
                             ProductionSiteService productionSites,
                             Clock clock) {
        this.productionPlanningService = productionPlanningService;
        this.productionSites = productionSites;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.planning.daily-cron}", zone = "${app.timezone}")
    public void generateDailyPlanPortugal() {
        runScheduledSite(ProductionSiteCode.PT, GenerationTrigger.SCHEDULED);
    }

    @Scheduled(cron = "${app.planning.daily-cron}", zone = "Europe/Luxembourg")
    public void generateDailyPlanLuxembourg() {
        runScheduledSite(ProductionSiteCode.LUX, GenerationTrigger.SCHEDULED);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverMissedCurrentDayPlan() {
        for (ProductionSiteEntity site : productionSites.activeSites()) {
            ZoneId zone = ZoneId.of(site.getTimezone());
            LocalDate today = LocalDate.now(clock.withZone(zone));
            LocalTime localTime = LocalTime.now(clock.withZone(zone));
            if (!localTime.isBefore(LocalTime.of(7, 0))
                    || productionPlanningService.needsInitialPlanning(site.getCode(), today)) {
                runForSite(site, today, GenerationTrigger.STARTUP_RECOVERY);
            }
        }
    }

    private void runScheduledSite(ProductionSiteCode siteCode, GenerationTrigger trigger) {
        ProductionSiteEntity site;
        try {
            site = productionSites.requireActive(siteCode);
        } catch (RuntimeException ex) {
            LOGGER.warn("Skipping scheduled production plan generation because site is unavailable site={}", siteCode, ex);
            return;
        }
        ZoneId zone = ZoneId.of(site.getTimezone());
        runForSite(site, LocalDate.now(clock.withZone(zone)), trigger);
    }

    private void runForSite(ProductionSiteEntity site, LocalDate date, GenerationTrigger trigger) {
        try {
            LOGGER.info("Running production plan generation site={} date={} trigger={}", site.getCode(), date, trigger);
            productionPlanningService.recalculate(site.getCode(), date, trigger, "SYSTEM");
        } catch (InvalidRequestException ex) {
            if ("TARGET_CONFIGURATION_MISSING".equals(ex.errorCode())) {
                LOGGER.warn("Skipping production plan generation because targets are not configured site={} date={} trigger={} code={}",
                        site.getCode(), date, trigger, ex.errorCode());
                return;
            }
            LOGGER.error("Production plan generation failed site={} date={} trigger={}",
                    site.getCode(), date, trigger, ex);
        } catch (RuntimeException ex) {
            LOGGER.error("Production plan generation failed site={} date={} trigger={}",
                    site.getCode(), date, trigger, ex);
        }
    }
}
