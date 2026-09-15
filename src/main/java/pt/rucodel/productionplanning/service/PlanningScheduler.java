package pt.rucodel.productionplanning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.rucodel.productionplanning.domain.GenerationTrigger;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class PlanningScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlanningScheduler.class);

    private final MultiDayProductionPlanningService productionPlanningService;
    private final Clock clock;
    private final ZoneId businessZone;

    public PlanningScheduler(MultiDayProductionPlanningService productionPlanningService, Clock clock, AppProperties appProperties) {
        this.productionPlanningService = productionPlanningService;
        this.clock = clock;
        this.businessZone = ZoneId.of(appProperties.timezone());
    }

    @Scheduled(cron = "${app.planning.daily-cron}", zone = "${app.timezone}")
    public void generateDailyPlan() {
        LocalDate today = LocalDate.now(clock.withZone(businessZone));
        LOGGER.info("Running scheduled production plan generation date={}", today);
        productionPlanningService.recalculate(today, GenerationTrigger.SCHEDULED, "SYSTEM");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverMissedCurrentDayPlan() {
        LocalDate today = LocalDate.now(clock.withZone(businessZone));
        LocalTime localTime = LocalTime.now(clock.withZone(businessZone));
        if (!localTime.isBefore(LocalTime.of(7, 0))) {
            LOGGER.info("Checking startup recovery production plan date={}", today);
            productionPlanningService.recalculate(today, GenerationTrigger.STARTUP_RECOVERY, "SYSTEM");
        }
    }
}
