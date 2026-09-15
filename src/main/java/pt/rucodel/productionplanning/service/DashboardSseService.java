package pt.rucodel.productionplanning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pt.rucodel.productionplanning.domain.DashboardPlanUpdatedEvent;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class DashboardSseService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardSseService.class);
    static final long HEARTBEAT_INTERVAL_MILLIS = 30_000L;
    private static final long TIMEOUT_MILLIS = 0L;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = createEmitter();
        registerEmitter(emitter);
        sendConnectedEvent(emitter);
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDashboardPlanUpdated(DashboardPlanUpdatedEvent event) {
        sendPlanUpdate(event);
    }

    public void sendPlanUpdate(DashboardPlanUpdatedEvent update) {
        sendToAll(SseEmitter.event()
                .name("production-plan-updated")
                .data(Map.of(
                        "type", "PRODUCTION_PLAN_UPDATED",
                        "date", update.planningDate().toString()
                )), "dashboard update");
    }

    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MILLIS)
    public void sendHeartbeat() {
        sendToAll(SseEmitter.event().comment("heartbeat"), "heartbeat");
    }

    public int activeClientCount() {
        return emitters.size();
    }

    protected SseEmitter createEmitter() {
        return new SseEmitter(TIMEOUT_MILLIS);
    }

    void registerEmitter(SseEmitter emitter) {
        emitters.add(emitter);
        LOGGER.info("Production planning SSE client connected activeClients={}", emitters.size());
        emitter.onCompletion(() -> removeEmitter(emitter, "completion"));
        emitter.onTimeout(() -> removeEmitter(emitter, "timeout"));
        emitter.onError(error -> removeEmitter(emitter, "error"));
    }

    private void sendConnectedEvent(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                    .name("dashboard-connected")
                    .data(Map.of(
                            "status", "DASHBOARD_CONNECTED",
                            "connected_at", OffsetDateTime.now(ZoneOffset.UTC).toString()
                    )));
        } catch (IOException | IllegalStateException exception) {
            removeEmitter(emitter, "failed connected event");
            LOGGER.debug("Removing SSE client after failed connected event", exception);
        }
    }

    private void sendToAll(SseEmitter.SseEventBuilder event, String eventDescription) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException exception) {
                removeEmitter(emitter, "failed " + eventDescription + " send");
                LOGGER.debug("Removing SSE client after failed {} send", eventDescription, exception);
            }
        }
    }

    private void removeEmitter(SseEmitter emitter, String reason) {
        if (emitters.remove(emitter)) {
            LOGGER.info("Production planning SSE client disconnected reason={} activeClients={}", reason, emitters.size());
        }
    }
}
