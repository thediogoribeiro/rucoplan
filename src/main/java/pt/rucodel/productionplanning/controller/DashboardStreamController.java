package pt.rucodel.productionplanning.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pt.rucodel.productionplanning.security.CurrentUserService;
import pt.rucodel.productionplanning.service.DashboardSseService;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class DashboardStreamController {
    private final DashboardSseService dashboardSseService;
    private final CurrentUserService currentUserService;

    public DashboardStreamController(DashboardSseService dashboardSseService, CurrentUserService currentUserService) {
        this.dashboardSseService = dashboardSseService;
        this.currentUserService = currentUserService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return dashboardSseService.subscribe(currentUserService.requireUser().productionSiteCode());
    }
}
