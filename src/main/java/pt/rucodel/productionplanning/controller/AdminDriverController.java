package pt.rucodel.productionplanning.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pt.rucodel.productionplanning.dto.DriverRequest;
import pt.rucodel.productionplanning.dto.DriverResponse;
import pt.rucodel.productionplanning.security.CurrentUserService;
import pt.rucodel.productionplanning.service.DriverService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/drivers")
public class AdminDriverController {
    private final DriverService drivers;
    private final CurrentUserService currentUserService;

    public AdminDriverController(DriverService drivers, CurrentUserService currentUserService) {
        this.drivers = drivers;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<DriverResponse> list() {
        return drivers.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DriverResponse create(@Valid @RequestBody DriverRequest request) {
        return drivers.create(request, currentUserService.requireUser().actorLabel());
    }

    @PatchMapping("/{driverId}")
    public DriverResponse update(@PathVariable UUID driverId, @Valid @RequestBody DriverRequest request) {
        return drivers.update(driverId, request, currentUserService.requireUser().actorLabel());
    }
}
