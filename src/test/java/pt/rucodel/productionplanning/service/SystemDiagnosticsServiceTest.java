package pt.rucodel.productionplanning.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.rucodel.productionplanning.domain.LifecycleStatus;
import pt.rucodel.productionplanning.domain.ProductionPlanStatus;
import pt.rucodel.productionplanning.dto.SystemDiagnosticsResponse;
import pt.rucodel.productionplanning.entity.ProductionTargetConfigurationEntity;
import pt.rucodel.productionplanning.repository.PlanningRunRepository;
import pt.rucodel.productionplanning.repository.ProductionPlanItemRepository;
import pt.rucodel.productionplanning.repository.ProductionPlanRepository;
import pt.rucodel.productionplanning.repository.WheelIntakeRequestRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemDiagnosticsServiceTest {
    @Test
    void diagnosticsReturnPartialPlanningDataWhenOneQueryFails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DashboardSseService sseService = mock(DashboardSseService.class);
        WheelIntakeRequestRepository requests = mock(WheelIntakeRequestRepository.class);
        ProductionPlanRepository plans = mock(ProductionPlanRepository.class);
        ProductionPlanItemRepository planItems = mock(ProductionPlanItemRepository.class);
        PlanningRunRepository planningRuns = mock(PlanningRunRepository.class);
        ProductionTargetService targetService = mock(ProductionTargetService.class);
        Environment environment = mock(Environment.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-24T08:00:00Z"), ZoneId.of("Europe/Lisbon"));

        ProductionTargetConfigurationEntity target = new ProductionTargetConfigurationEntity();
        target.setMinimumDailyTarget(150);
        target.setRegularDailyCapacity(180);
        target.setEffectiveFrom(LocalDate.of(2026, 9, 24));
        target.setCreatedBy("SYSTEM");

        when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);
        when(sseService.diagnostics()).thenReturn(new SystemDiagnosticsResponse.RealtimeDiagnostics(
                "UP", "SSE", "/api/v1/admin/dashboard/stream",
                null, null, null, 0, null, null, null, null, "UNKNOWN", null, 0
        ));
        when(requests.countByLifecycleStatus(LifecycleStatus.COMMUNICATED))
                .thenThrow(new DataAccessResourceFailureException("count failed"));
        when(plans.countByCurrentPlanTrueAndStatusNot(ProductionPlanStatus.CLOSED)).thenReturn(2L);
        when(planItems.count()).thenReturn(6L);
        when(plans.findMaxCurrentPlanningDate()).thenReturn(LocalDate.of(2026, 9, 25));
        when(planningRuns.findFirstByOrderByStartedAtDesc()).thenReturn(Optional.empty());
        when(targetService.effectiveFor(LocalDate.of(2026, 9, 24))).thenReturn(target);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});

        SystemDiagnosticsService service = new SystemDiagnosticsService(
                jdbcTemplate,
                sseService,
                requests,
                plans,
                planItems,
                planningRuns,
                targetService,
                clock,
                new AppProperties("RucoPlan", "Europe/Lisbon"),
                environment,
                "test-version"
        );

        SystemDiagnosticsResponse response = service.diagnostics();

        assertThat(response.backend().status()).isEqualTo("UP");
        assertThat(response.database().status()).isEqualTo("UP");
        assertThat(response.planning().confirmedRequests()).isNull();
        assertThat(response.planning().confirmedRequestsErrorCode()).isEqualTo("PLANNING_COMMUNICATED_REQUESTS_QUERY_FAILED");
        assertThat(response.planning().openPlans()).isEqualTo(2L);
        assertThat(response.planning().openPlansErrorCode()).isNull();
        assertThat(response.planning().planLines()).isEqualTo(6L);
        assertThat(response.planning().latestPlanDate()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(response.planning().targetsUsed().minimumDailyTarget()).isEqualTo(150);
        assertThat(response.planning().correlationId()).isNotBlank();
    }
}
