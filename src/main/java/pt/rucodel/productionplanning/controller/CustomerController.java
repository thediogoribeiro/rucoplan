package pt.rucodel.productionplanning.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pt.rucodel.productionplanning.dto.CustomerRequest;
import pt.rucodel.productionplanning.dto.CustomerResponse;
import pt.rucodel.productionplanning.security.CurrentUserService;
import pt.rucodel.productionplanning.service.CustomerService;

import java.util.List;
import java.util.UUID;

@RestController
public class CustomerController {
    private final CustomerService customers;
    private final CurrentUserService currentUserService;

    public CustomerController(CustomerService customers, CurrentUserService currentUserService) {
        this.customers = customers;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/api/v1/customers/search")
    public List<CustomerResponse> search(@RequestParam(defaultValue = "") String q,
                                         @RequestParam(defaultValue = "20") int limit) {
        return customers.search(currentUserService.requireUser().productionSiteCode(), q, limit);
    }

    @GetMapping("/api/v1/admin/customers")
    public List<CustomerResponse> list() {
        return customers.list(currentUserService.requireUser().productionSiteCode());
    }

    @PostMapping("/api/v1/admin/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        var user = currentUserService.requireUser();
        return customers.create(user.productionSiteCode(), request, user.actorLabel());
    }

    @PatchMapping("/api/v1/admin/customers/{customerId}")
    public CustomerResponse update(@PathVariable UUID customerId, @Valid @RequestBody CustomerRequest request) {
        var user = currentUserService.requireUser();
        return customers.update(user.productionSiteCode(), customerId, request, user.actorLabel());
    }
}
