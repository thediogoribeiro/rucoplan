package pt.rucodel.productionplanning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pt.rucodel.productionplanning.domain.LifecycleStatus;
import pt.rucodel.productionplanning.domain.PlanningTargetDefaults;
import pt.rucodel.productionplanning.domain.ProductionPlanStatus;
import pt.rucodel.productionplanning.dto.SystemDiagnosticsResponse;
import pt.rucodel.productionplanning.entity.PlanningRunEntity;
import pt.rucodel.productionplanning.entity.ProductionTargetConfigurationEntity;
import pt.rucodel.productionplanning.repository.PlanningRunRepository;
import pt.rucodel.productionplanning.repository.ProductionPlanItemRepository;
import pt.rucodel.productionplanning.repository.ProductionPlanRepository;
import pt.rucodel.productionplanning.repository.WheelIntakeRequestRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class SystemDiagnosticsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SystemDiagnosticsService.class);

    private final JdbcTemplate jdbcTemplate;
    private final DashboardSseService dashboardSseService;
    private final WheelIntakeRequestRepository requests;
    private final ProductionPlanRepository plans;
    private final ProductionPlanItemRepository planItems;
    private final PlanningRunRepository planningRuns;
    private final ProductionTargetService targetService;
    private final Clock clock;
    private final ZoneId businessZone;
    private final String version;
    private final Environment environment;

    public SystemDiagnosticsService(JdbcTemplate jdbcTemplate,
                                    DashboardSseService dashboardSseService,
                                    WheelIntakeRequestRepository requests,
                                    ProductionPlanRepository plans,
                                    ProductionPlanItemRepository planItems,
                                    PlanningRunRepository planningRuns,
                                    ProductionTargetService targetService,
                                    Clock clock,
                                    AppProperties appProperties,
                                    Environment environment,
                                    @Value("${info.app.version:dev}") String version) {
        this.jdbcTemplate = jdbcTemplate;
        this.dashboardSseService = dashboardSseService;
        this.requests = requests;
        this.plans = plans;
        this.planItems = planItems;
        this.planningRuns = planningRuns;
        this.targetService = targetService;
        this.clock = clock;
        this.businessZone = ZoneId.of(appProperties.timezone());
        this.environment = environment;
        this.version = version;
    }

    public SystemDiagnosticsResponse diagnostics() {
        long started = System.nanoTime();
        OffsetDateTime checkedAt = OffsetDateTime.now(clock);
        SystemDiagnosticsResponse.DatabaseDiagnostics database = databaseDiagnostics();
        SystemDiagnosticsResponse.PlanningDiagnostics planning = "UP".equals(database.status())
                ? planningDiagnostics()
                : unavailablePlanningDiagnostics();
        long latencyMs = elapsedMs(started);
        return new SystemDiagnosticsResponse(
                checkedAt,
                new SystemDiagnosticsResponse.BackendDiagnostics(
                        "UP",
                        version,
                        activeProfiles(),
                        OffsetDateTime.now(clock),
                        latencyMs,
                        "UP".equals(database.status()) ? "UP" : "DATABASE_UNAVAILABLE"
                ),
                database,
                realtimeDiagnostics(),
                planning
        );
    }

    public SystemDiagnosticsResponse.DatabaseDiagnostics databaseDiagnostics() {
        long started = System.nanoTime();
        OffsetDateTime checkedAt = OffsetDateTime.now(clock);
        try {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            SchemaCheck schema = schemaCheck();
            return new SystemDiagnosticsResponse.DatabaseDiagnostics(
                    "UP",
                    checkedAt,
                    elapsedMs(started),
                    null,
                    null,
                    optionalString("select current_database()"),
                    optionalString("select current_schema()"),
                    schema.status(),
                    schema.errorCode()
            );
        } catch (DataAccessException ex) {
            String correlationId = UUID.randomUUID().toString();
            LOGGER.error("Database diagnostic failed correlationId={}", correlationId, ex);
            return new SystemDiagnosticsResponse.DatabaseDiagnostics(
                    "DOWN",
                    checkedAt,
                    elapsedMs(started),
                    "DATABASE_UNAVAILABLE",
                    correlationId,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    public SystemDiagnosticsResponse.RealtimeDiagnostics realtimeDiagnostics() {
        return dashboardSseService.diagnostics();
    }

    private SystemDiagnosticsResponse.PlanningDiagnostics planningDiagnostics() {
        DiagnosticValue<Long> confirmedRequests = queryDiagnostic("confirmed requests count",
                "PLANNING_COMMUNICATED_REQUESTS_QUERY_FAILED",
                () -> requests.countByLifecycleStatus(LifecycleStatus.COMMUNICATED));
        DiagnosticValue<Long> openPlans = queryDiagnostic("open plans count",
                "PLANNING_OPEN_PLANS_QUERY_FAILED",
                () -> plans.countByCurrentPlanTrueAndStatusNot(ProductionPlanStatus.CLOSED));
        DiagnosticValue<Long> planLines = queryDiagnostic("plan lines count",
                "PLANNING_LINES_QUERY_FAILED",
                planItems::count);
        DiagnosticValue<LocalDate> latestPlanDate = queryDiagnostic("latest plan date",
                "PLANNING_LATEST_PLAN_QUERY_FAILED",
                plans::findMaxCurrentPlanningDate);
        DiagnosticValue<PlanningRunEntity> lastRun = queryDiagnostic("last planning run",
                "PLANNING_LAST_RUN_QUERY_FAILED",
                () -> planningRuns.findFirstByOrderByStartedAtDesc().orElse(null));
        DiagnosticValue<SystemDiagnosticsResponse.TargetSnapshot> target = queryDiagnostic("active planning targets",
                "PLANNING_TARGETS_QUERY_FAILED",
                this::targetSnapshot);
        String correlationId = firstCorrelationId(
                confirmedRequests,
                openPlans,
                planLines,
                latestPlanDate,
                lastRun,
                target
        );
        PlanningRunEntity run = lastRun.value();
        return new SystemDiagnosticsResponse.PlanningDiagnostics(
                confirmedRequests.value(),
                confirmedRequests.errorCode(),
                openPlans.value(),
                openPlans.errorCode(),
                planLines.value(),
                planLines.errorCode(),
                latestPlanDate.value(),
                latestPlanDate.errorCode(),
                run == null ? null : run.getStartedAt(),
                run == null ? (lastRun.errorCode() == null ? null : "UNKNOWN") : run.getStatus().name(),
                run == null ? null : run.getTrigger().name(),
                lastRun.errorCode(),
                target.value(),
                target.errorCode(),
                correlationId,
                run == null ? null : run.getSummary()
        );
    }

    private SystemDiagnosticsResponse.PlanningDiagnostics unavailablePlanningDiagnostics() {
        return new SystemDiagnosticsResponse.PlanningDiagnostics(
                null,
                "PLANNING_UNAVAILABLE",
                null,
                "PLANNING_UNAVAILABLE",
                null,
                "PLANNING_UNAVAILABLE",
                null,
                "PLANNING_UNAVAILABLE",
                null,
                "UNKNOWN",
                null,
                "PLANNING_UNAVAILABLE",
                null,
                "PLANNING_UNAVAILABLE",
                null,
                null
        );
    }

    private SchemaCheck schemaCheck() {
        try {
            jdbcTemplate.queryForObject("select count(*) from wheel_intake_request where 1 = 0", Long.class);
            jdbcTemplate.queryForObject("select count(*) from request_wheel_quantity where 1 = 0", Long.class);
            jdbcTemplate.queryForObject("select count(*) from production_plan where 1 = 0", Long.class);
            jdbcTemplate.queryForObject("select count(*) from production_plan_item where 1 = 0", Long.class);
            jdbcTemplate.queryForObject("select count(*) from production_plan_item_wheel_quantity where 1 = 0", Long.class);
            return new SchemaCheck("UP", null);
        } catch (DataAccessException ex) {
            String correlationId = UUID.randomUUID().toString();
            LOGGER.error("Database schema diagnostic failed correlationId={}", correlationId, ex);
            return new SchemaCheck("DOWN", "DATABASE_SCHEMA_INVALID");
        }
    }

    private String optionalString(String sql) {
        try {
            return jdbcTemplate.queryForObject(sql, String.class);
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private SystemDiagnosticsResponse.TargetSnapshot targetSnapshot() {
        ProductionTargetConfigurationEntity target = targetService.effectiveFor(LocalDate.now(clock.withZone(businessZone)));
        return new SystemDiagnosticsResponse.TargetSnapshot(
                target.getMinimumDailyTarget(),
                target.getRegularDailyCapacity(),
                target.isSystemDefault()
                        ? PlanningTargetDefaults.SOURCE_SYSTEM_DEFAULT
                        : PlanningTargetDefaults.SOURCE_DATABASE
        );
    }

    private <T> DiagnosticValue<T> queryDiagnostic(String name, String errorCode, Supplier<T> supplier) {
        try {
            return new DiagnosticValue<>(supplier.get(), null, null);
        } catch (DataAccessException ex) {
            String correlationId = UUID.randomUUID().toString();
            LOGGER.error("Planning diagnostic query failed component={} code={} correlationId={}",
                    name, errorCode, correlationId, ex);
            return new DiagnosticValue<>(null, errorCode, correlationId);
        }
    }

    @SafeVarargs
    private final String firstCorrelationId(DiagnosticValue<?>... values) {
        return Arrays.stream(values)
                .map(DiagnosticValue::correlationId)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private record DiagnosticValue<T>(T value, String errorCode, String correlationId) {
    }

    private record SchemaCheck(String status, String errorCode) {
    }

    private String activeProfiles() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "default" : String.join(",", Arrays.asList(profiles));
    }

    private long elapsedMs(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
