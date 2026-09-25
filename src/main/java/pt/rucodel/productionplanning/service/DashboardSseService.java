package pt.rucodel.productionplanning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pt.rucodel.productionplanning.domain.DashboardPlanUpdatedEvent;
import pt.rucodel.productionplanning.dto.SystemDiagnosticsResponse;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class DashboardSseService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardSseService.class);
    static final long HEARTBEAT_INTERVAL_MILLIS = 30_000L;
    private static final long TIMEOUT_MILLIS = 0L;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private volatile OffsetDateTime lastClientConnectedAt;
    private volatile OffsetDateTime lastEventAt;
    private volatile OffsetDateTime lastFailureAt;
    private volatile OffsetDateTime lastHeartbeatAt;
    private volatile String lastErrorCode;
    private volatile String lastCorrelationId;

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

    public SystemDiagnosticsResponse.RealtimeDiagnostics diagnostics() {
        String status = !emitters.isEmpty()
                ? "UP"
                : lastFailureAt == null ? "UNKNOWN" : "DOWN";
        String heartbeatStatus = lastHeartbeatAt == null ? "UNKNOWN" : "ACTIVE";
        return new SystemDiagnosticsResponse.RealtimeDiagnostics(
                status,
                "SSE",
                "/api/v1/admin/dashboard/stream",
                lastClientConnectedAt,
                lastEventAt,
                lastFailureAt,
                0,
                null,
                null,
                lastErrorCode,
                lastCorrelationId,
                heartbeatStatus,
                lastHeartbeatAt,
                activeClientCount()
        );
    }

    protected SseEmitter createEmitter() {
        return new SseEmitter(TIMEOUT_MILLIS);
    }

    void registerEmitter(SseEmitter emitter) {
        emitters.add(emitter);
        lastClientConnectedAt = OffsetDateTime.now(ZoneOffset.UTC);
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
            lastEventAt = OffsetDateTime.now(ZoneOffset.UTC);
        } catch (IOException | IllegalStateException exception) {
            removeEmitter(emitter, "failed connected event", exception);
            LOGGER.debug("Removing SSE client after failed connected event", exception);
        }
    }

    private void sendToAll(SseEmitter.SseEventBuilder event, String eventDescription) {
        if ("heartbeat".equals(eventDescription)) {
            lastHeartbeatAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(event);
                if (!"heartbeat".equals(eventDescription)) {
                    lastEventAt = OffsetDateTime.now(ZoneOffset.UTC);
                }
            } catch (IOException | IllegalStateException exception) {
                removeEmitter(emitter, "failed " + eventDescription + " send", exception);
                LOGGER.debug("Removing SSE client after failed {} send", eventDescription, exception);
            }
        }
    }

    private void removeEmitter(SseEmitter emitter, String reason) {
        removeEmitter(emitter, reason, null);
    }

    private void removeEmitter(SseEmitter emitter, String reason, Throwable error) {
        if (emitters.remove(emitter)) {
            if (error != null || reason.contains("error") || reason.contains("failed")) {
                lastFailureAt = OffsetDateTime.now(ZoneOffset.UTC);
                lastErrorCode = "REALTIME_CONNECTION_FAILED";
                lastCorrelationId = UUID.randomUUID().toString();
                LOGGER.warn("Production planning SSE client failed reason={} correlationId={} activeClients={}",
                        reason, lastCorrelationId, emitters.size());
            }
            LOGGER.info("Production planning SSE client disconnected reason={} activeClients={}", reason, emitters.size());
        }
    }
}
