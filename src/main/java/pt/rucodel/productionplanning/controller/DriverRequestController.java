package pt.rucodel.productionplanning.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pt.rucodel.productionplanning.dto.PageResponse;
import pt.rucodel.productionplanning.dto.RequestCreateRequest;
import pt.rucodel.productionplanning.dto.RequestResponse;
import pt.rucodel.productionplanning.dto.RequestUpdateRequest;
import pt.rucodel.productionplanning.security.AuthenticatedUser;
import pt.rucodel.productionplanning.security.CurrentUserService;
import pt.rucodel.productionplanning.service.WheelIntakeRequestService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/driver/requests")
public class DriverRequestController {
    private final WheelIntakeRequestService requests;
    private final CurrentUserService currentUserService;

    public DriverRequestController(WheelIntakeRequestService requests, CurrentUserService currentUserService) {
        this.requests = requests;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public PageResponse<RequestResponse> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size) {
        return requests.listForDriver(currentUserService.requireDriverId(), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequestResponse create(@Valid @RequestBody RequestCreateRequest request) {
        AuthenticatedUser user = currentUserService.requireUser();
        return requests.createForDriver(request, user);
    }

    @GetMapping("/{requestId}")
    public RequestResponse get(@PathVariable UUID requestId) {
        return requests.getForDriver(requestId, currentUserService.requireDriverId());
    }

    @PatchMapping("/{requestId}")
    public RequestResponse update(@PathVariable UUID requestId, @Valid @RequestBody RequestUpdateRequest request) {
        AuthenticatedUser user = currentUserService.requireUser();
        return requests.updateForDriver(requestId, user.driverId(), request, user);
    }

    @PostMapping("/{requestId}/cancel")
    public RequestResponse cancel(@PathVariable UUID requestId, @Valid @RequestBody RequestUpdateRequest request) {
        AuthenticatedUser user = currentUserService.requireUser();
        return requests.cancelForDriver(requestId, user.driverId(), request, user);
    }
}
