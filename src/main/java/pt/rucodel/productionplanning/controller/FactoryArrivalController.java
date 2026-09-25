package pt.rucodel.productionplanning.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import pt.rucodel.productionplanning.domain.LifecycleStatus;
import pt.rucodel.productionplanning.dto.ConfirmArrivalRequest;
import pt.rucodel.productionplanning.dto.RequestResponse;
import pt.rucodel.productionplanning.security.CurrentUserService;
import pt.rucodel.productionplanning.service.WheelIntakeRequestService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/factory-arrivals")
public class FactoryArrivalController {
    private final WheelIntakeRequestService requests;
    private final CurrentUserService currentUserService;

    public FactoryArrivalController(WheelIntakeRequestService requests, CurrentUserService currentUserService) {
        this.requests = requests;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<RequestResponse> list(@RequestParam(required = false) LifecycleStatus status) {
        return requests.listFactoryArrivals(status);
    }

    @GetMapping("/{requestId}")
    public RequestResponse get(@PathVariable UUID requestId) {
        return requests.getForAdmin(requestId);
    }

    @PostMapping("/{requestId}/confirm")
    public RequestResponse confirm(@PathVariable UUID requestId, @Valid @RequestBody ConfirmArrivalRequest request) {
        return requests.confirmArrival(requestId, request, currentUserService.requireUser());
    }
}
