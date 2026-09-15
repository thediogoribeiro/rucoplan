package pt.rucodel.productionplanning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.IngestionStatus;
import pt.rucodel.productionplanning.dto.WhatsAppIngestionResponse;
import pt.rucodel.productionplanning.dto.WhatsAppIntakeRequest;
import pt.rucodel.productionplanning.entity.CustomerReferenceEntity;
import pt.rucodel.productionplanning.entity.DriverEntity;
import pt.rucodel.productionplanning.entity.WhatsAppIngestionItemEntity;
import pt.rucodel.productionplanning.entity.WheelIntakeRequestEntity;
import pt.rucodel.productionplanning.exception.InvalidRequestException;
import pt.rucodel.productionplanning.integration.CustomerDirectoryCustomer;
import pt.rucodel.productionplanning.integration.CustomerDirectoryPort;
import pt.rucodel.productionplanning.repository.CustomerReferenceRepository;
import pt.rucodel.productionplanning.repository.DriverRepository;
import pt.rucodel.productionplanning.repository.WhatsAppIngestionItemRepository;
import pt.rucodel.productionplanning.repository.WheelIntakeRequestRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class WhatsAppIngestionService {
    private final String integrationToken;
    private final WhatsAppIngestionItemRepository ingestionItems;
    private final WheelIntakeRequestRepository requests;
    private final DriverRepository drivers;
    private final CustomerReferenceRepository customerReferences;
    private final CustomerDirectoryPort customerDirectory;
    private final WheelIntakeRequestService intakeRequests;
    private final AuditService auditService;
    private final Clock clock;

    public WhatsAppIngestionService(@Value("${app.integrations.whatsapp.token}") String integrationToken,
                                    WhatsAppIngestionItemRepository ingestionItems,
                                    WheelIntakeRequestRepository requests,
                                    DriverRepository drivers,
                                    CustomerReferenceRepository customerReferences,
                                    CustomerDirectoryPort customerDirectory,
                                    WheelIntakeRequestService intakeRequests,
                                    AuditService auditService,
                                    Clock clock) {
        this.integrationToken = integrationToken;
        this.ingestionItems = ingestionItems;
        this.requests = requests;
        this.drivers = drivers;
        this.customerReferences = customerReferences;
        this.customerDirectory = customerDirectory;
        this.intakeRequests = intakeRequests;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public WhatsAppIngestionResponse ingest(WhatsAppIntakeRequest request, String suppliedToken) {
        requireValidToken(suppliedToken);
        return ingestionItems.findByExternalMessageId(request.externalMessageId())
                .map(existing -> new WhatsAppIngestionResponse(existing.getId(), IngestionStatus.DUPLICATE,
                        existing.getRequestId(), existing.getExternalMessageId(), "Message was already processed."))
                .orElseGet(() -> ingestNew(request));
    }

    private WhatsAppIngestionResponse ingestNew(WhatsAppIntakeRequest request) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        DriverEntity driver = drivers.findByExternalId(request.driverExternalId())
                .filter(DriverEntity::isActive)
                .orElse(null);
        if (driver == null) {
            return saveReviewItem(request, IngestionStatus.NEEDS_REVIEW,
                    "Driver external ID was not resolved.");
        }
        CustomerReferenceEntity customer = resolveCustomer(request);
        if (customer == null) {
            return saveReviewItem(request, IngestionStatus.NEEDS_REVIEW,
                    "Customer could not be resolved uniquely.");
        }
        try {
            WheelIntakeRequestEntity created = intakeRequests.createFromIntegration(
                    request.externalMessageId(),
                    driver,
                    customer,
                    request,
                    "WHATSAPP_AGENT"
            );
            WhatsAppIngestionItemEntity item = baseItem(request, now);
            item.setStatus(IngestionStatus.CREATED);
            item.setRequestId(created.getId());
            item.setReason("Request created.");
            WhatsAppIngestionItemEntity saved = ingestionItems.save(item);
            auditService.record(null, created.getId(), "WHATSAPP_INGESTED", "WHATSAPP_AGENT",
                    "WhatsApp message " + request.externalMessageId() + " created request.");
            return new WhatsAppIngestionResponse(saved.getId(), IngestionStatus.CREATED, created.getId(),
                    request.externalMessageId(), "Request created.");
        } catch (DataIntegrityViolationException ex) {
            return requests.findByExternalMessageId(request.externalMessageId())
                    .map(existing -> new WhatsAppIngestionResponse(null, IngestionStatus.DUPLICATE,
                            existing.getId(), request.externalMessageId(), "Message was already processed."))
                    .orElseThrow(() -> ex);
        }
    }

    private CustomerReferenceEntity resolveCustomer(WhatsAppIntakeRequest request) {
        if (request.customerExternalId() != null && !request.customerExternalId().isBlank()) {
            return customerDirectory.resolveByExternalId(request.customerExternalId().trim())
                    .flatMap(customer -> customerReferences.findById(customer.localId()))
                    .orElse(null);
        }
        List<CustomerDirectoryCustomer> exactMatches = customerDirectory.resolveByExactName(request.customerName());
        if (exactMatches.size() != 1) {
            return null;
        }
        return customerReferences.findById(exactMatches.getFirst().localId()).orElse(null);
    }

    private WhatsAppIngestionResponse saveReviewItem(WhatsAppIntakeRequest request, IngestionStatus status, String reason) {
        try {
            WhatsAppIngestionItemEntity item = baseItem(request, OffsetDateTime.now(clock));
            item.setStatus(status);
            item.setReason(reason);
            WhatsAppIngestionItemEntity saved = ingestionItems.save(item);
            auditService.record(null, null, "WHATSAPP_NEEDS_REVIEW", "WHATSAPP_AGENT",
                    "WhatsApp message " + request.externalMessageId() + " needs review: " + reason);
            return new WhatsAppIngestionResponse(saved.getId(), status, null, request.externalMessageId(), reason);
        } catch (DataIntegrityViolationException ex) {
            return ingestionItems.findByExternalMessageId(request.externalMessageId())
                    .map(existing -> new WhatsAppIngestionResponse(existing.getId(), IngestionStatus.DUPLICATE,
                            existing.getRequestId(), existing.getExternalMessageId(), "Message was already processed."))
                    .orElseThrow(() -> ex);
        }
    }

    private WhatsAppIngestionItemEntity baseItem(WhatsAppIntakeRequest request, OffsetDateTime now) {
        WhatsAppIngestionItemEntity item = new WhatsAppIngestionItemEntity();
        item.setExternalMessageId(request.externalMessageId());
        item.setDriverExternalId(request.driverExternalId());
        item.setCustomerExternalId(request.customerExternalId());
        item.setCustomerName(request.customerName());
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return item;
    }

    private void requireValidToken(String suppliedToken) {
        if (integrationToken == null || integrationToken.isBlank()) {
            throw new InvalidRequestException("AUTHENTICATION_FAILED", "WhatsApp integration token is not configured.");
        }
        String clean = suppliedToken == null ? "" : suppliedToken.trim();
        if (clean.startsWith("Bearer ")) {
            clean = clean.substring("Bearer ".length()).trim();
        }
        if (!MessageDigest.isEqual(integrationToken.getBytes(StandardCharsets.UTF_8), clean.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidRequestException("AUTHENTICATION_FAILED", "Invalid WhatsApp integration credential.");
        }
    }
}
