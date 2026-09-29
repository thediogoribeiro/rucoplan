package pt.rucodel.productionplanning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.rucodel.productionplanning.dto.SystemDiagnosticsResponse;
import pt.rucodel.productionplanning.security.CurrentUserService;
import pt.rucodel.productionplanning.service.SystemDiagnosticsService;

@RestController
@RequestMapping("/api/v1/admin/system-diagnostics")
public class SystemDiagnosticsController {
    private final SystemDiagnosticsService diagnostics;
    private final CurrentUserService currentUserService;

    public SystemDiagnosticsController(SystemDiagnosticsService diagnostics, CurrentUserService currentUserService) {
        this.diagnostics = diagnostics;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public SystemDiagnosticsResponse diagnostics() {
        return diagnostics.diagnostics(currentUserService.requireUser().productionSiteCode());
    }

    @GetMapping("/realtime")
    public SystemDiagnosticsResponse.RealtimeDiagnostics realtime() {
        return diagnostics.realtimeDiagnostics();
    }

    @GetMapping("/database")
    public SystemDiagnosticsResponse.DatabaseDiagnostics database() {
        return diagnostics.databaseDiagnostics();
    }
}
