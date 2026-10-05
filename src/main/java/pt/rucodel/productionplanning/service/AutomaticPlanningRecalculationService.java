package pt.rucodel.productionplanning.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pt.rucodel.productionplanning.domain.GenerationTrigger;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.domain.RecalculationStatus;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Service
public class AutomaticPlanningRecalculationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutomaticPlanningRecalculationService.class);

    private final MultiDayProductionPlanningService productionPlanning;
    private final DashboardEventPublisher dashboardEvents;
    private final ExecutorService executor;
    private final Map<ProductionSiteCode, RecalculationState> states = new EnumMap<>(ProductionSiteCode.class);

    @Autowired
    public AutomaticPlanningRecalculationService(MultiDayProductionPlanningService productionPlanning,
                                                 DashboardEventPublisher dashboardEvents) {
        this(productionPlanning, dashboardEvents, Executors.newFixedThreadPool(2, new RecalculationThreadFactory()));
    }

    AutomaticPlanningRecalculationService(MultiDayProductionPlanningService productionPlanning,
                                          DashboardEventPublisher dashboardEvents,
                                          ExecutorService executor) {
        this.productionPlanning = productionPlanning;
        this.dashboardEvents = dashboardEvents;
        this.executor = executor;
        for (ProductionSiteCode site : ProductionSiteCode.values()) {
            states.put(site, new RecalculationState());
        }
    }

    public String schedule(ProductionSiteCode siteCode, LocalDate affectedDate, String reason) {
        String correlationId = UUID.randomUUID().toString();
        RecalculationState state = states.get(siteCode);
        synchronized (state) {
            if (state.running) {
                state.pendingDate = earlier(state.pendingDate, affectedDate);
                state.pendingReason = mergeReason(state.pendingReason, reason);
                LOGGER.info("Automatic production plan recalculation coalesced site={} affectedDate={} reason={} correlationId={}",
                        siteCode, affectedDate, reason, correlationId);
                return correlationId;
            }
            state.running = true;
        }
        executor.submit(() -> runUntilIdle(siteCode, affectedDate, reason, correlationId));
        return correlationId;
    }

    private void runUntilIdle(ProductionSiteCode siteCode, LocalDate affectedDate, String reason, String correlationId) {
        LocalDate nextDate = affectedDate;
        String nextReason = reason;
        String nextCorrelationId = correlationId;
        while (true) {
            runOnce(siteCode, nextDate, nextReason, nextCorrelationId);
            RecalculationState state = states.get(siteCode);
            synchronized (state) {
                if (state.pendingDate == null) {
                    state.running = false;
                    return;
                }
                nextDate = state.pendingDate;
                nextReason = state.pendingReason;
                nextCorrelationId = UUID.randomUUID().toString();
                state.pendingDate = null;
                state.pendingReason = null;
            }
        }
    }

    private void runOnce(ProductionSiteCode siteCode, LocalDate affectedDate, String reason, String correlationId) {
        LOGGER.info("Automatic production plan recalculation started site={} affectedDate={} reason={} correlationId={}",
                siteCode, affectedDate, reason, correlationId);
        dashboardEvents.publishRecalculationStatus(siteCode, affectedDate, RecalculationStatus.STARTED, reason, correlationId, null);
        try {
            productionPlanning.recalculate(siteCode, affectedDate, GenerationTrigger.AUTOMATIC_RECALCULATION, "SYSTEM");
            dashboardEvents.publishRecalculationStatus(siteCode, affectedDate, RecalculationStatus.COMPLETED, reason, correlationId, null);
            LOGGER.info("Automatic production plan recalculation completed site={} affectedDate={} reason={} correlationId={}",
                    siteCode, affectedDate, reason, correlationId);
        } catch (RuntimeException exception) {
            dashboardEvents.publishRecalculationStatus(siteCode, affectedDate, RecalculationStatus.FAILED, reason, correlationId,
                    exception.getClass().getSimpleName());
            LOGGER.warn("Automatic production plan recalculation failed site={} affectedDate={} reason={} correlationId={}",
                    siteCode, affectedDate, reason, correlationId, exception);
        }
    }

    private LocalDate earlier(LocalDate current, LocalDate candidate) {
        if (current == null) {
            return candidate;
        }
        return candidate.isBefore(current) ? candidate : current;
    }

    private String mergeReason(String current, String next) {
        if (current == null || current.isBlank()) {
            return next;
        }
        if (next == null || next.isBlank() || current.contains(next)) {
            return current;
        }
        return current + "," + next;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static final class RecalculationState {
        private boolean running;
        private LocalDate pendingDate;
        private String pendingReason;
    }

    private static final class RecalculationThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName("auto-planning-recalculation");
            thread.setDaemon(true);
            return thread;
        }
    }
}
