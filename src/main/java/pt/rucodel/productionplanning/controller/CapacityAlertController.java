package pt.rucodel.productionplanning.controller;

import org.springframework.web.bind.annotation.*;
import pt.rucodel.productionplanning.dto.CapacityAlertResponse;
import pt.rucodel.productionplanning.security.CurrentUserService;
import pt.rucodel.productionplanning.service.CapacityAlertService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/capacity-alerts")
public class CapacityAlertController {
    private final CapacityAlertService alerts;
    private final CurrentUserService currentUserService;

    public CapacityAlertController(CapacityAlertService alerts, CurrentUserService currentUserService) {
        this.alerts = alerts;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<CapacityAlertResponse> listOpen() {
        return alerts.listOpen(currentUserService.requireUser().productionSiteCode());
    }

    @PatchMapping("/{id}/acknowledge")
    public CapacityAlertResponse acknowledge(@PathVariable UUID id) {
        return alerts.acknowledge(currentUserService.requireUser().productionSiteCode(), id);
    }
}
