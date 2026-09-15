package pt.rucodel.productionplanning.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pt.rucodel.productionplanning.domain.LifecycleStatus;
import pt.rucodel.productionplanning.dto.*;
import pt.rucodel.productionplanning.security.AuthenticatedUser;
import pt.rucodel.productionplanning.security.CurrentUserService;
import pt.rucodel.productionplanning.service.WheelIntakeRequestService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/requests")
public class AdminRequestController {
    private final WheelIntakeRequestService requests;
    private final CurrentUserService currentUserService;

    public AdminRequestController(WheelIntakeRequestService requests, CurrentUserService currentUserService) {
        this.requests = requests;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public PageResponse<RequestResponse> list(@RequestParam(required = false) UUID driverId,
                                              @RequestParam(required = false) UUID customerId,
                                              @RequestParam(required = false) LifecycleStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "100") int size) {
        return requests.listForAdmin(driverId, customerId, status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequestResponse create(@Valid @RequestBody AdminRequestCreateRequest request) {
        return requests.createForAdmin(request, currentUserService.requireUser());
    }

    @GetMapping("/{requestId}")
    public RequestResponse get(@PathVariable UUID requestId) {
        return requests.getForAdmin(requestId);
    }

    @PatchMapping("/{requestId}")
    public RequestResponse update(@PathVariable UUID requestId, @Valid @RequestBody RequestUpdateRequest request) {
        return requests.updateForAdmin(requestId, request, currentUserService.requireUser());
    }

    @PostMapping("/{requestId}/arrival")
    public RequestResponse confirmArrival(@PathVariable UUID requestId, @Valid @RequestBody ConfirmArrivalRequest request) {
        return requests.confirmArrival(requestId, request, currentUserService.requireUser());
    }

    @PostMapping("/{requestId}/received-quantity")
    public RequestResponse confirmReceivedQuantity(@PathVariable UUID requestId,
                                                   @Valid @RequestBody ConfirmReceivedQuantityRequest request) {
        return requests.confirmReceivedQuantity(requestId, request, currentUserService.requireUser());
    }

    @PostMapping("/{requestId}/status")
    public RequestResponse updateStatus(@PathVariable UUID requestId, @Valid @RequestBody UpdateStatusRequest request) {
        return requests.updateStatus(requestId, request, currentUserService.requireUser());
    }

    @PostMapping("/{requestId}/priority")
    public RequestResponse updatePriority(@PathVariable UUID requestId, @Valid @RequestBody PriorityRequest request) {
        return requests.updatePriority(requestId, request, currentUserService.requireUser());
    }

    @GetMapping("/{requestId}/history")
    public List<StatusHistoryResponse> history(@PathVariable UUID requestId) {
        return requests.statusHistory(requestId);
    }
}
