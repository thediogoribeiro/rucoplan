package pt.rucodel.productionplanning.service;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pt.rucodel.productionplanning.domain.DashboardPlanUpdatedEvent;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardSseServiceTest {
    @Test
    void subscribeSendsConnectedEvent() {
        CountingDashboardSseService service = new CountingDashboardSseService(new CountingEmitter());

        SseEmitter emitter = service.subscribe();

        assertThat(service.activeClientCount()).isEqualTo(1);
        assertThat(emitter.getTimeout()).isZero();
        assertThat(service.emitter.sentPayloads.getFirst()).contains("dashboard-connected");
    }

    @Test
    void heartbeatIsCommentOnlyAndRunsEveryThirtySeconds() throws NoSuchMethodException {
        CountingDashboardSseService service = new CountingDashboardSseService(new CountingEmitter());
        service.subscribe();

        service.sendHeartbeat();

        assertThat(service.emitter.sentPayloads.getLast())
                .contains("heartbeat")
                .doesNotContain("data:");
        Method method = DashboardSseService.class.getMethod("sendHeartbeat");
        assertThat(method.getAnnotation(Scheduled.class).fixedRate()).isEqualTo(30_000L);
    }

    @Test
    void planUpdatesAreSentAfterCommit() throws NoSuchMethodException {
        CountingDashboardSseService service = new CountingDashboardSseService(new CountingEmitter());
        service.subscribe();

        service.onDashboardPlanUpdated(new DashboardPlanUpdatedEvent(LocalDate.of(2026, 9, 2)));

        assertThat(service.emitter.sentPayloads.getLast()).contains("production-plan-updated");
        Method method = DashboardSseService.class.getMethod("onDashboardPlanUpdated", DashboardPlanUpdatedEvent.class);
        assertThat(method.getAnnotation(TransactionalEventListener.class).phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void failedEmittersAreRemoved() {
        CountingDashboardSseService service = new CountingDashboardSseService(new FailingAfterFirstSendEmitter());
        service.subscribe();

        service.sendHeartbeat();

        assertThat(service.activeClientCount()).isZero();
    }

    private static class CountingDashboardSseService extends DashboardSseService {
        private final List<TestEmitter> emitters;
        private final TestEmitter emitter;
        private int nextEmitterIndex;

        private CountingDashboardSseService(TestEmitter... emitters) {
            this.emitters = Arrays.asList(emitters);
            this.emitter = emitters[0];
        }

        @Override
        protected SseEmitter createEmitter() {
            return emitters.get(nextEmitterIndex++);
        }
    }

    private abstract static class TestEmitter extends SseEmitter {
        private final java.util.ArrayList<String> sentPayloads = new java.util.ArrayList<>();

        private TestEmitter() {
            super(0L);
        }

        protected void record(SseEventBuilder builder) {
            String payload = builder.build().stream()
                    .map(data -> data.getData().toString())
                    .reduce("", String::concat);
            sentPayloads.add(payload);
        }
    }

    private static class CountingEmitter extends TestEmitter {
        @Override
        public void send(SseEventBuilder builder) throws IOException {
            record(builder);
        }
    }

    private static class FailingAfterFirstSendEmitter extends TestEmitter {
        private int count;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            count++;
            record(builder);
            if (count > 1) {
                throw new IOException("send failed");
            }
        }
    }
}
