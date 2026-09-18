package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.CustomerRegistrationStatus;
import pt.rucodel.productionplanning.domain.CustomerStatus;
import pt.rucodel.productionplanning.entity.*;
import pt.rucodel.productionplanning.exception.EntityNotFoundException;
import pt.rucodel.productionplanning.exception.InvalidRequestException;
import pt.rucodel.productionplanning.repository.CustomerReferenceRepository;
import pt.rucodel.productionplanning.repository.CustomerRegistrationRequestRepository;
import pt.rucodel.productionplanning.repository.WheelIntakeRequestRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
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

    @Transactional
    public CustomerRegistrationRequestEntity startDraft(String proposedName, DriverEntity driver,
                                                        MessagingIdentityEntity identity,
                                                        TelegramConversationEntity conversation) {
        String displayName = normalizer.displayInput(proposedName);
        String normalized = normalizer.normalize(displayName);
        if (normalized.isBlank()) {
            throw new InvalidRequestException("Customer name cannot be empty.");
        }
        CustomerRegistrationRequestEntity request = registrations
                .findFirstByConversationIdAndStatusOrderByCreatedAtDesc(conversation.getId(), CustomerRegistrationStatus.PENDING_REVIEW)
                .orElseGet(CustomerRegistrationRequestEntity::new);
        request.setProposedName(displayName);
        request.setNormalizedName(normalized);
        request.setRequestedByDriver(driver);
        request.setRequestedByIdentity(identity);
        request.setConversation(conversation);
        request.setStatus(CustomerRegistrationStatus.PENDING_REVIEW);
        request.setCreatedBy("TELEGRAM");
        request.setUpdatedBy("TELEGRAM");
        return registrations.saveAndFlush(request);
    }

    @Transactional(noRollbackFor = InvalidRequestException.class)
    public CustomerRegistrationRequestEntity saveTaxIdentifier(CustomerRegistrationRequestEntity registration, String value) {
        String normalized = normalizeTaxIdentifier(value);
        if (normalized == null) {
            throw new InvalidRequestException("O NIF/VAT é obrigatório.");
        }
        registration.setTaxIdentifier(normalized);
        registration.setUpdatedBy("TELEGRAM");
        return registrations.saveAndFlush(registration);
    }

    @Transactional(noRollbackFor = InvalidRequestException.class)
    public CustomerRegistrationRequestEntity saveCountry(CustomerRegistrationRequestEntity registration, String value) {
        String countryCode = normalizeCountryCode(value);
        if (countryCode == null) {
            throw new InvalidRequestException("O país é obrigatório.");
        }
        registration.setCountryCode(countryCode);
        registration.setUpdatedBy("TELEGRAM");
        validateTaxForCountry(registration.getTaxIdentifier(), countryCode);
        return registrations.saveAndFlush(registration);
    }

    @Transactional(noRollbackFor = InvalidRequestException.class)
    public CustomerRegistrationRequestEntity saveLocalityAndReserveNumber(CustomerRegistrationRequestEntity registration, String value) {
        String locality = normalizeLocality(value);
        if (locality == null) {
            throw new InvalidRequestException("A localidade é obrigatória.");
        }
        registration.setLocality(locality);
        if (registration.getReservedCustomerNumber() == null) {
            registration.setReservedCustomerNumber(customers.nextCustomerNumber());
        }
        registration.setUpdatedBy("TELEGRAM");
        return registrations.saveAndFlush(registration);
    }

    @Transactional
    public CustomerRegistrationRequestEntity reserveNumber(CustomerRegistrationRequestEntity registration) {
        if (registration.getReservedCustomerNumber() == null) {
            registration.setReservedCustomerNumber(customers.nextCustomerNumber());
            registration.setUpdatedBy("TELEGRAM");
            return registrations.saveAndFlush(registration);
        }
        return registration;
    }

    @Transactional(noRollbackFor = InvalidRequestException.class)
    public CustomerReferenceEntity confirmAndCreateCustomer(CustomerRegistrationRequestEntity registration) {
        if (registration.getMatchedCustomer() != null && registration.getStatus() == CustomerRegistrationStatus.APPROVED) {
            return registration.getMatchedCustomer();
        }
        validateComplete(registration);
        if (registration.getReservedCustomerNumber() == null) {
            reserveNumber(registration);
        }
        List<CustomerReferenceEntity> exactNameMatches = customers.findByActiveTrueAndNormalizedNameOrderByNameAscIdAsc(
                registration.getNormalizedName());
        if (!exactNameMatches.isEmpty()) {
            throw new InvalidRequestException("Entretanto foi encontrado um cliente com este nome. Confirme antes de continuar.");
        }
        List<CustomerReferenceEntity> taxMatches = customers.findByActiveTrueAndTaxIdentifierAndCountryCodeOrderByNameAscIdAsc(
                normalizeTaxIdentifier(registration.getTaxIdentifier()), normalizeCountryCode(registration.getCountryCode()));
        if (!taxMatches.isEmpty()) {
            throw new InvalidRequestException("Entretanto foi encontrado um cliente com este NIF/VAT e país. Confirme antes de continuar.");
        }
        CustomerReferenceEntity existingNumber = customers.findByCustomerNumber(registration.getReservedCustomerNumber()).orElse(null);
        if (existingNumber != null) {
            registration.setMatchedCustomer(existingNumber);
            registration.setStatus(CustomerRegistrationStatus.APPROVED);
            registration.setReviewedAt(OffsetDateTime.now(clock));
            registration.setReviewedBy("TELEGRAM");
            registration.setUpdatedBy("TELEGRAM");
            return existingNumber;
        }
        CustomerReferenceEntity customer = new CustomerReferenceEntity();
        customer.setCustomerNumber(registration.getReservedCustomerNumber());
        customer.setName(registration.getProposedName());
        customer.setNormalizedName(registration.getNormalizedName());
        customer.setTaxIdentifier(normalizeTaxIdentifier(registration.getTaxIdentifier()));
        customer.setCountryCode(normalizeCountryCode(registration.getCountryCode()));
        customer.setLocality(normalizeLocality(registration.getLocality()));
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setActive(true);
        customer.setCreatedBy("TELEGRAM");
        customer.setUpdatedBy("TELEGRAM");
        CustomerReferenceEntity saved = customers.saveAndFlush(customer);
        registration.setMatchedCustomer(saved);
        registration.setStatus(CustomerRegistrationStatus.APPROVED);
        registration.setReviewedAt(OffsetDateTime.now(clock));
        registration.setReviewedBy("TELEGRAM");
        registration.setUpdatedBy("TELEGRAM");
        auditService.record(null, null, "CUSTOMER_CREATED", "TELEGRAM",
                "Cliente criado pelo bot Telegram com número " + saved.getCustomerNumber() + ".");
        return saved;
    }

    @Transactional
    public CustomerRegistrationRequestEntity cancel(CustomerRegistrationRequestEntity registration) {
        registration.setStatus(CustomerRegistrationStatus.CANCELLED);
        registration.setReviewedAt(OffsetDateTime.now(clock));
        registration.setReviewedBy("TELEGRAM");
        registration.setUpdatedBy("TELEGRAM");
        return registrations.saveAndFlush(registration);
    }

    @Transactional(readOnly = true)
    public CustomerRegistrationRequestEntity currentForConversation(TelegramConversationEntity conversation) {
        return registrations.findFirstByConversationIdAndStatusOrderByCreatedAtDesc(
                        conversation.getId(), CustomerRegistrationStatus.PENDING_REVIEW)
                .orElseThrow(() -> new InvalidRequestException("O registo de cliente em curso já não está disponível."));
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

    public String normalizeTaxIdentifier(String value) {
        String clean = value == null ? null : value.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return clean == null || clean.isBlank() ? null : clean;
    }

    public String normalizeCountryCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = normalizer.normalize(value).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PORTUGAL", "PT" -> "PT";
            case "ESPANHA", "SPAIN", "ES" -> "ES";
            case "FRANCA", "FRANCE", "FR" -> "FR";
            case "LUXEMBURGO", "LUXEMBOURG", "LU" -> "LU";
            default -> normalized.length() == 2 && normalized.matches("[A-Z]{2}") ? normalized : null;
        };
    }

    public String countryName(String countryCode) {
        return switch (countryCode == null ? "" : countryCode) {
            case "PT" -> "Portugal";
            case "ES" -> "Espanha";
            case "FR" -> "França";
            case "LU" -> "Luxemburgo";
            default -> countryCode;
        };
    }

    private String normalizeLocality(String value) {
        String clean = value == null ? null : value.trim().replaceAll("\\s+", " ");
        if (clean == null || clean.isBlank()) {
            return null;
        }
        if (!clean.matches("^[\\p{L}\\p{M}0-9 .'-]{2,120}$")) {
            throw new InvalidRequestException("A localidade contém caracteres inválidos.");
        }
        return clean;
    }

    private void validateComplete(CustomerRegistrationRequestEntity registration) {
        if (registration.getProposedName() == null || registration.getProposedName().isBlank()
                || normalizeTaxIdentifier(registration.getTaxIdentifier()) == null
                || normalizeCountryCode(registration.getCountryCode()) == null
                || normalizeLocality(registration.getLocality()) == null) {
            throw new InvalidRequestException("Os dados do cliente estão incompletos.");
        }
        validateTaxForCountry(registration.getTaxIdentifier(), registration.getCountryCode());
    }

    private void validateTaxForCountry(String taxIdentifier, String countryCode) {
        String normalizedTax = normalizeTaxIdentifier(taxIdentifier);
        if (normalizedTax == null) {
            throw new InvalidRequestException("O NIF/VAT é obrigatório.");
        }
        if ("PT".equals(countryCode) && !normalizedTax.matches("[0-9]{9}")) {
            throw new InvalidRequestException("O NIF português deve ter 9 dígitos.");
        }
        if (!normalizedTax.matches("[A-Z0-9]{2,30}")) {
            throw new InvalidRequestException("O NIF/VAT deve conter apenas letras e números.");
        }
    }
}
