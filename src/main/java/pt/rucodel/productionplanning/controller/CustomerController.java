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
        return customers.search(q, limit);
    }

    @GetMapping("/api/v1/admin/customers")
    public List<CustomerResponse> list() {
        return customers.list();
    }

    @PostMapping("/api/v1/admin/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return customers.create(request, currentUserService.requireUser().actorLabel());
    }

    @PatchMapping("/api/v1/admin/customers/{customerId}")
    public CustomerResponse update(@PathVariable UUID customerId, @Valid @RequestBody CustomerRequest request) {
        return customers.update(customerId, request, currentUserService.requireUser().actorLabel());
    }
}
