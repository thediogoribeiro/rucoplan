package pt.rucodel.productionplanning.controller;

import org.springframework.web.bind.annotation.*;
import pt.rucodel.productionplanning.dto.CustomerRegistrationDecisionRequest;
import pt.rucodel.productionplanning.dto.CustomerRegistrationRequestResponse;
import pt.rucodel.productionplanning.entity.CustomerRegistrationRequestEntity;
import pt.rucodel.productionplanning.security.CurrentUserService;
import pt.rucodel.productionplanning.service.CustomerRegistrationRequestService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/customer-registration-requests")
public class AdminCustomerRegistrationController {
    private final CustomerRegistrationRequestService registrations;
    private final CurrentUserService currentUserService;

    public AdminCustomerRegistrationController(CustomerRegistrationRequestService registrations,
                                               CurrentUserService currentUserService) {
        this.registrations = registrations;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<CustomerRegistrationRequestResponse> pending() {
        return registrations.pending().stream().map(this::toResponse).toList();
    }

    @PostMapping("/{registrationId}/link")
    public CustomerRegistrationRequestResponse link(@PathVariable UUID registrationId,
                                                    @RequestBody CustomerRegistrationDecisionRequest request) {
        CustomerRegistrationRequestEntity entity = registrations.linkToExisting(
                registrationId,
                request.customerId(),
                currentUserService.requireUser().actorLabel(),
                request.notes()
        );
        return toResponse(entity);
    }

    @PostMapping("/{registrationId}/reject")
    public CustomerRegistrationRequestResponse reject(@PathVariable UUID registrationId,
                                                      @RequestBody CustomerRegistrationDecisionRequest request) {
        CustomerRegistrationRequestEntity entity = registrations.reject(
                registrationId,
                currentUserService.requireUser().actorLabel(),
                request.notes()
        );
        return toResponse(entity);
    }

    private CustomerRegistrationRequestResponse toResponse(CustomerRegistrationRequestEntity entity) {
        return new CustomerRegistrationRequestResponse(
                entity.getId(),
                entity.getProposedName(),
                entity.getCustomerNumber(),
                mask(entity.getTaxIdentifier()),
                entity.getCountryCode(),
                entity.getLocality(),
                entity.getRequestedByDriver().getId(),
                entity.getRequestedByDriver().getName(),
                entity.getRequestedByIdentity() == null ? null : entity.getRequestedByIdentity().getId(),
                entity.getStatus(),
                entity.getMatchedCustomer() == null ? null : entity.getMatchedCustomer().getId(),
                entity.getMatchedCustomer() == null ? null : entity.getMatchedCustomer().getName(),
                entity.getCreatedAt(),
                entity.getReviewedAt(),
                entity.getReviewedBy(),
                entity.getReviewNotes(),
                entity.getVersion()
        );
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return "***";
        }
        return trimmed.substring(0, 2) + "***" + trimmed.substring(trimmed.length() - 2);
    }
}
