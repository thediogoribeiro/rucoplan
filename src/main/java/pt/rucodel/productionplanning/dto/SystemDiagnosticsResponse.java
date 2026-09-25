package pt.rucodel.productionplanning.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SystemDiagnosticsResponse(
        OffsetDateTime checkedAt,
        BackendDiagnostics backend,
        DatabaseDiagnostics database,
        RealtimeDiagnostics realtime,
        PlanningDiagnostics planning
) {
    public record BackendDiagnostics(
            String status,
            String version,
            String environment,
            OffsetDateTime lastResponseAt,
            Long latencyMs,
            String planningEndpointStatus
    ) {
    }

    public record DatabaseDiagnostics(
            String status,
            OffsetDateTime checkedAt,
            Long latencyMs,
            String errorCode,
            String correlationId,
            String databaseName,
            String schemaName,
            String schemaStatus,
            String schemaErrorCode
    ) {
    }

    public record RealtimeDiagnostics(
            String status,
            String transport,
            String endpoint,
            OffsetDateTime lastConnectedAt,
            OffsetDateTime lastEventAt,
            OffsetDateTime lastFailureAt,
            Integer attempts,
            OffsetDateTime nextRetryAt,
            Integer httpStatus,
            String errorCode,
            String correlationId,
            String heartbeatStatus,
            OffsetDateTime lastHeartbeatAt,
            int activeClients
    ) {
    }

    public record PlanningDiagnostics(
            Long confirmedRequests,
            String confirmedRequestsErrorCode,
            Long openPlans,
            String openPlansErrorCode,
            Long planLines,
            String planLinesErrorCode,
            LocalDate latestPlanDate,
            String latestPlanDateErrorCode,
            OffsetDateTime lastPlanningRunAt,
            String lastPlanningRunStatus,
            String lastPlanningRunTrigger,
            String lastPlanningRunErrorCode,
            TargetSnapshot targetsUsed,
            String targetsErrorCode,
            String correlationId,
            String lastPlanningRunReason
    ) {
    }

    public record TargetSnapshot(
            int minimumDailyTarget,
            int regularDailyCapacity,
            String source
    ) {
    }
}
