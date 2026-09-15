package pt.rucodel.productionplanning.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pt.rucodel.productionplanning.service.DashboardSseService;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class DashboardStreamController {
    private final DashboardSseService dashboardSseService;

    public DashboardStreamController(DashboardSseService dashboardSseService) {
        this.dashboardSseService = dashboardSseService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return dashboardSseService.subscribe();
    }
}
