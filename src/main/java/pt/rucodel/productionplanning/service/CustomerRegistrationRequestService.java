package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.CustomerRegistrationStatus;
import pt.rucodel.productionplanning.entity.*;
import pt.rucodel.productionplanning.exception.EntityNotFoundException;
import pt.rucodel.productionplanning.exception.InvalidRequestException;
import pt.rucodel.productionplanning.repository.CustomerReferenceRepository;
import pt.rucodel.productionplanning.repository.CustomerRegistrationRequestRepository;
import pt.rucodel.productionplanning.repository.WheelIntakeRequestRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerRegistrationRequestService {
    private final CustomerRegistrationRequestRepository registrations;
    private final CustomerReferenceRepository customers;
    private final WheelIntakeRequestRepository requests;
    private final CustomerNameNormalizer normalizer;
    private final AuditService auditService;
    private final Clock clock;

    public CustomerRegistrationRequestService(CustomerRegistrationRequestRepository registrations,
                                              CustomerReferenceRepository customers,
                                              WheelIntakeRequestRepository requests,
                                              CustomerNameNormalizer normalizer,
                                              AuditService auditService,
                                              Clock clock) {
        this.registrations = registrations;
        this.customers = customers;
        this.requests = requests;
        this.normalizer = normalizer;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public CustomerRegistrationRequestEntity createPending(String proposedName, DriverEntity driver,
                                                           MessagingIdentityEntity identity,
                                                           TelegramConversationEntity conversation) {
        String displayName = normalizer.displayInput(proposedName);
        String normalized = normalizer.normalize(displayName);
        if (normalized.isBlank()) {
            throw new InvalidRequestException("Customer name cannot be empty.");
        }
        CustomerRegistrationRequestEntity existing = registrations
                .findByNormalizedNameAndStatus(normalized, CustomerRegistrationStatus.PENDING_REVIEW)
                .stream()
                .filter(request -> request.getRequestedByDriver().getId().equals(driver.getId()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        CustomerRegistrationRequestEntity request = new CustomerRegistrationRequestEntity();
        request.setProposedName(displayName);
        request.setNormalizedName(normalized);
        request.setRequestedByDriver(driver);
        request.setRequestedByIdentity(identity);
        request.setConversation(conversation);
        request.setStatus(CustomerRegistrationStatus.PENDING_REVIEW);
        request.setCreatedBy("TELEGRAM");
        request.setUpdatedBy("TELEGRAM");
        CustomerRegistrationRequestEntity saved = registrations.saveAndFlush(request);
        auditService.record(null, null, "CUSTOMER_REGISTRATION_REQUESTED", "TELEGRAM",
                "Registo provisório de cliente criado: " + mask(displayName));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CustomerRegistrationRequestEntity> pending() {
        return registrations.findByStatusOrderByCreatedAtAsc(CustomerRegistrationStatus.PENDING_REVIEW);
    }

    @Transactional
    public CustomerRegistrationRequestEntity linkToExisting(UUID registrationId, UUID customerId, String actor, String notes) {
        CustomerRegistrationRequestEntity registration = registrations.findById(registrationId)
                .orElseThrow(() -> new EntityNotFoundException("Customer registration request was not found."));
        if (registration.getStatus() != CustomerRegistrationStatus.PENDING_REVIEW) {
            return registration;
        }
        CustomerReferenceEntity customer = customers.findById(customerId).filter(CustomerReferenceEntity::isActive)
                .orElseThrow(() -> new EntityNotFoundException("Customer was not found."));
        registration.setMatchedCustomer(customer);
        registration.setStatus(CustomerRegistrationStatus.LINKED_TO_EXISTING);
        registration.setReviewedAt(OffsetDateTime.now(clock));
        registration.setReviewedBy(actor);
        registration.setReviewNotes(notes);
        registration.setUpdatedBy(actor);
        requests.findByCustomerRegistrationRequestId(registrationId).forEach(request -> {
            request.setCustomer(customer);
            request.setCustomerExternalId(customer.getExternalId());
            request.setCustomerNameSnapshot(customer.getName());
            request.setUpdatedBy(actor);
            requests.save(request);
            auditService.record(null, request.getId(), "CUSTOMER_REGISTRATION_LINKED", actor,
                    "Cliente provisório associado a cliente existente.");
        });
        return registration;
    }

    @Transactional
    public CustomerRegistrationRequestEntity reject(UUID registrationId, String actor, String notes) {
        CustomerRegistrationRequestEntity registration = registrations.findById(registrationId)
                .orElseThrow(() -> new EntityNotFoundException("Customer registration request was not found."));
        if (registration.getStatus() != CustomerRegistrationStatus.PENDING_REVIEW) {
            return registration;
        }
        registration.setStatus(CustomerRegistrationStatus.REJECTED);
        registration.setReviewedAt(OffsetDateTime.now(clock));
        registration.setReviewedBy(actor);
        registration.setReviewNotes(notes);
        registration.setUpdatedBy(actor);
        return registration;
    }

    private String mask(String value) {
        return value.length() <= 3 ? "***" : value.substring(0, Math.min(3, value.length())) + "***";
    }
}
