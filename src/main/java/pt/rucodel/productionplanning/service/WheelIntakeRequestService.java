package pt.rucodel.productionplanning.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.*;
import pt.rucodel.productionplanning.dto.*;
import pt.rucodel.productionplanning.entity.CustomerReferenceEntity;
import pt.rucodel.productionplanning.entity.DriverEntity;
import pt.rucodel.productionplanning.entity.MessagingIdentityEntity;
import pt.rucodel.productionplanning.entity.RequestStatusHistoryEntity;
import pt.rucodel.productionplanning.entity.WheelIntakeRequestEntity;
import pt.rucodel.productionplanning.exception.EntityNotFoundException;
import pt.rucodel.productionplanning.exception.ForbiddenOperationException;
import pt.rucodel.productionplanning.exception.InvalidRequestException;
import pt.rucodel.productionplanning.mapper.ApiMapper;
import pt.rucodel.productionplanning.repository.CustomerReferenceRepository;
import pt.rucodel.productionplanning.repository.DriverRepository;
import pt.rucodel.productionplanning.repository.RequestStatusHistoryRepository;
import pt.rucodel.productionplanning.repository.WheelIntakeRequestRepository;
import pt.rucodel.productionplanning.security.AuthenticatedUser;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class WheelIntakeRequestService {
    private static final EnumSet<LifecycleStatus> DRIVER_EDITABLE_STATUSES = EnumSet.of(LifecycleStatus.REGISTERED);
    private static final EnumSet<LifecycleStatus> CLOSED_STATUSES = EnumSet.of(
            LifecycleStatus.PICKED_UP_FROM_FACTORY,
            LifecycleStatus.CANCELLED
    );

    private final WheelIntakeRequestRepository requests;
    private final CustomerReferenceRepository customers;
    private final DriverRepository drivers;
    private final RequestStatusHistoryRepository statusHistory;
    private final ApiMapper mapper;
    private final Clock clock;
    private final RecalculationService recalculationService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final CapacityAlertService capacityAlertService;
    private final WheelQuantityService wheelQuantityService;

    public WheelIntakeRequestService(WheelIntakeRequestRepository requests, CustomerReferenceRepository customers,
                                     DriverRepository drivers, RequestStatusHistoryRepository statusHistory,
                                     ApiMapper mapper, Clock clock, RecalculationService recalculationService,
                                     AuditService auditService, ApplicationEventPublisher eventPublisher,
                                     CapacityAlertService capacityAlertService, WheelQuantityService wheelQuantityService) {
        this.requests = requests;
        this.customers = customers;
        this.drivers = drivers;
        this.statusHistory = statusHistory;
        this.mapper = mapper;
        this.clock = clock;
        this.recalculationService = recalculationService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.capacityAlertService = capacityAlertService;
        this.wheelQuantityService = wheelQuantityService;
    }

    @Transactional
    public RequestResponse createForDriver(RequestCreateRequest request, AuthenticatedUser user) {
        DriverEntity driver = drivers.findById(user.driverId())
                .orElseThrow(() -> new EntityNotFoundException("Driver was not found."));
        WheelIntakeRequestEntity entity = createEntity(
                RequestSource.WEB,
                null,
                null,
                driver,
                requireCustomer(request.customerId()),
                wheelQuantityService.normalize(request.wheelQuantities(), request.expectedWheelQuantity()),
                request.expectedFactoryDropOffWindowStart(),
                request.expectedFactoryDropOffWindowEnd(),
                request.requestedFactoryPickupWindowStart(),
                request.requestedFactoryPickupWindowEnd(),
                request.notes(),
                user.actorLabel()
        );
        return persistCreated(entity, user);
    }

    @Transactional
    public RequestResponse createForAdmin(AdminRequestCreateRequest request, AuthenticatedUser user) {
        DriverEntity driver = drivers.findById(request.driverId())
                .orElseThrow(() -> new EntityNotFoundException("Driver was not found."));
        WheelIntakeRequestEntity entity = createEntity(
                RequestSource.WEB,
                null,
                null,
                driver,
                requireCustomer(request.customerId()),
                wheelQuantityService.normalize(request.wheelQuantities(), request.expectedWheelQuantity()),
                request.expectedFactoryDropOffWindowStart(),
                request.expectedFactoryDropOffWindowEnd(),
                request.requestedFactoryPickupWindowStart(),
                request.requestedFactoryPickupWindowEnd(),
                request.notes(),
                user.actorLabel()
        );
        entity.setManualPriority(request.manualPriority());
        entity.setPlanningLocked(Boolean.TRUE.equals(request.planningLocked()));
        return persistCreated(entity, user);
    }

    @Transactional
    public WheelIntakeRequestEntity createFromIntegration(String externalMessageId, DriverEntity driver,
                                                         CustomerReferenceEntity customer, WhatsAppIntakeRequest request,
                                                         String actor) {
        WheelIntakeRequestEntity entity = createEntity(
                RequestSource.WHATSAPP_AGENT,
                "WHATSAPP_AGENT",
                externalMessageId,
                driver,
                customer,
                wheelQuantityService.normalize(request.wheelQuantities(), request.wheelQuantity()),
                request.expectedFactoryDropOffWindowStart(),
                request.expectedFactoryDropOffWindowEnd(),
                request.requestedFactoryPickupWindowStart(),
                request.requestedFactoryPickupWindowEnd(),
                request.notes(),
                actor
        );
        WheelIntakeRequestEntity saved = requests.save(entity);
        recordStatus(saved, null, LifecycleStatus.REGISTERED, null, actor, "Criado pelo agente WhatsApp.");
        recalculationService.markCurrentAndFuturePlans();
        capacityAlertService.recalculateFromToday();
        auditService.record(null, saved.getId(), "REQUEST_CREATED", actor, "Request created from WhatsApp agent.");
        return saved;
    }

    @Transactional
    public WheelIntakeRequestEntity createFromTelegram(String externalMessageId, DriverEntity driver,
                                                       CustomerReferenceEntity customer, int expectedQuantity,
                                                       OffsetDateTime dropOffStart, OffsetDateTime dropOffEnd,
                                                       FactoryTimeSlot dropoffSlot,
                                                       OffsetDateTime pickupStart, OffsetDateTime pickupEnd,
                                                       FactoryTimeSlot pickupSlot,
                                                       String notes) {
        return createFromTelegram(externalMessageId, driver, customer,
                Map.of(WheelType.NORMAL, expectedQuantity),
                dropOffStart, dropOffEnd, dropoffSlot, pickupStart, pickupEnd, pickupSlot, notes);
    }

    @Transactional
    public WheelIntakeRequestEntity createFromTelegram(String externalMessageId, DriverEntity driver,
                                                       MessagingIdentityEntity submittedByIdentity,
                                                       CustomerReferenceEntity customer, Map<WheelType, Integer> quantities,
                                                       OffsetDateTime dropOffStart, OffsetDateTime dropOffEnd,
                                                       FactoryTimeSlot dropoffSlot,
                                                       OffsetDateTime pickupStart, OffsetDateTime pickupEnd,
                                                       FactoryTimeSlot pickupSlot,
                                                       String notes) {
        return createFromTelegramInternal(externalMessageId, driver, submittedByIdentity, customer, quantities,
                dropOffStart, dropOffEnd, dropoffSlot, pickupStart, pickupEnd, pickupSlot, notes);
    }

    @Transactional
    public WheelIntakeRequestEntity createFromTelegram(String externalMessageId, DriverEntity driver,
                                                       CustomerReferenceEntity customer, Map<WheelType, Integer> quantities,
                                                       OffsetDateTime dropOffStart, OffsetDateTime dropOffEnd,
                                                       FactoryTimeSlot dropoffSlot,
                                                       OffsetDateTime pickupStart, OffsetDateTime pickupEnd,
                                                       FactoryTimeSlot pickupSlot,
                                                       String notes) {
        return createFromTelegramInternal(externalMessageId, driver, null, customer, quantities,
                dropOffStart, dropOffEnd, dropoffSlot, pickupStart, pickupEnd, pickupSlot, notes);
    }

    private WheelIntakeRequestEntity createFromTelegramInternal(String externalMessageId, DriverEntity driver,
                                                               MessagingIdentityEntity submittedByIdentity,
                                                               CustomerReferenceEntity customer, Map<WheelType, Integer> quantities,
                                                               OffsetDateTime dropOffStart, OffsetDateTime dropOffEnd,
                                                               FactoryTimeSlot dropoffSlot,
                                                               OffsetDateTime pickupStart, OffsetDateTime pickupEnd,
                                                               FactoryTimeSlot pickupSlot,
                                                               String notes) {
        String idempotencyKey = blankToNull(externalMessageId);
        if (idempotencyKey != null) {
            java.util.Optional<WheelIntakeRequestEntity> existing = requests.findByExternalMessageId(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        WheelIntakeRequestEntity entity = createEntity(
                RequestSource.TELEGRAM,
                "TELEGRAM",
                idempotencyKey,
                driver,
                customer,
                quantities,
                dropOffStart,
                dropOffEnd,
                pickupStart,
                pickupEnd,
                notes,
                "TELEGRAM"
        );
        entity.setSubmittedByIdentity(submittedByIdentity);
        entity.setFactoryDropoffSlot(dropoffSlot);
        entity.setFactoryPickupSlot(pickupSlot);
        WheelIntakeRequestEntity saved = requests.saveAndFlush(entity);
        recordStatus(saved, null, LifecycleStatus.REGISTERED, null, "TELEGRAM", "Criado pelo bot Telegram.");
        recalculationService.markCurrentAndFuturePlans();
        capacityAlertService.recalculateFromToday();
        auditService.record(null, saved.getId(), "REQUEST_CREATED", "TELEGRAM", "Request created from Telegram bot.");
        eventPublisher.publishEvent(new WheelIntakeRequestConfirmedEvent(saved.getId(), RequestSource.TELEGRAM));
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestResponse> listForDriver(UUID driverId, int page, int size) {
        return mapper.toPage(
                requests.findByDriverId(driverId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.ASC, "requestedFactoryPickupWindowStart"))),
                mapper::toRequest
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestResponse> listForAdmin(UUID driverId, UUID customerId, LifecycleStatus status, int page, int size) {
        return mapper.toPage(
                requests.search(driverId, customerId, status, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))),
                mapper::toRequest
        );
    }

    @Transactional(readOnly = true)
    public RequestResponse getForDriver(UUID requestId, UUID driverId) {
        WheelIntakeRequestEntity entity = requireRequest(requestId);
        requireDriverOwnership(entity, driverId);
        return mapper.toRequest(entity);
    }

    @Transactional(readOnly = true)
    public RequestResponse getForAdmin(UUID requestId) {
        return mapper.toRequest(requireRequest(requestId));
    }

    @Transactional
    public RequestResponse updateForDriver(UUID requestId, UUID driverId, RequestUpdateRequest update, AuthenticatedUser user) {
        WheelIntakeRequestEntity entity = requireRequest(requestId);
        requireDriverOwnership(entity, driverId);
        if (!DRIVER_EDITABLE_STATUSES.contains(entity.getLifecycleStatus()) || entity.getActualFactoryArrivalAt() != null) {
            throw new ForbiddenOperationException("Driver requests can only be edited before the wheels are confirmed at the factory.");
        }
        applyUpdate(entity, update, false, user.actorLabel());
        recalculationService.markCurrentAndFuturePlans();
        capacityAlertService.recalculateFromToday();
        auditService.record(null, entity.getId(), "REQUEST_UPDATED", user.actorLabel(), "Driver updated request.");
        return mapper.toRequest(requests.saveAndFlush(entity));
    }

    @Transactional
    public RequestResponse updateForAdmin(UUID requestId, RequestUpdateRequest update, AuthenticatedUser user) {
        WheelIntakeRequestEntity entity = requireRequest(requestId);
        applyUpdate(entity, update, true, user.actorLabel());
        recalculationService.markCurrentAndFuturePlans();
        capacityAlertService.recalculateFromToday();
        auditService.record(null, entity.getId(), "REQUEST_UPDATED", user.actorLabel(), "Administrator updated request.");
        return mapper.toRequest(requests.saveAndFlush(entity));
    }

    @Transactional
    public RequestResponse cancelForDriver(UUID requestId, UUID driverId, RequestUpdateRequest request, AuthenticatedUser user) {
        WheelIntakeRequestEntity entity = requireRequest(requestId);
        requireDriverOwnership(entity, driverId);
        requireVersion(entity, request.version());
        if (entity.getActualFactoryArrivalAt() != null || entity.getLifecycleStatus() != LifecycleStatus.REGISTERED) {
            throw new ForbiddenOperationException("Only future requests that have not arrived at the factory can be cancelled by the driver.");
        }
        if (!entity.getExpectedFactoryDropOffWindowStart().isAfter(OffsetDateTime.now(clock))) {
            throw new ForbiddenOperationException("Only future requests can be cancelled by the driver.");
        }
        changeStatus(entity, LifecycleStatus.CANCELLED, user, "Pedido cancelado pelo motorista/vendedor.");
        recalculationService.markCurrentAndFuturePlans();
        RequestResponse response = mapper.toRequest(requests.saveAndFlush(entity));
        capacityAlertService.recalculateFromToday();
        return response;
    }

    @Transactional
    public RequestResponse confirmArrival(UUID requestId, ConfirmArrivalRequest request, AuthenticatedUser user) {
        WheelIntakeRequestEntity entity = requireRequest(requestId);
        requireVersion(entity, request.version());
        entity.setActualFactoryArrivalAt(request.actualFactoryArrivalAt());
        if (request.actualReceivedWheelQuantity() != null) {
            entity.setActualReceivedWheelQuantity(request.actualReceivedWheelQuantity());
            entity.setQuantityDiscrepancyAcknowledged(discrepancyAcknowledged(entity, request.acknowledgeDiscrepancy()));
        }
        entity.setUpdatedBy(user.actorLabel());
        if (entity.getLifecycleStatus() == LifecycleStatus.REGISTERED) {
            changeStatus(entity, LifecycleStatus.ARRIVED_AT_FACTORY, user, request.reason());
        } else {
            auditService.record(null, entity.getId(), "REQUEST_ARRIVAL_CONFIRMED", user.actorLabel(), "Factory arrival confirmed.");
        }
        recalculationService.markCurrentAndFuturePlans();
        RequestResponse response = mapper.toRequest(requests.saveAndFlush(entity));
        capacityAlertService.recalculateFromToday();
        return response;
    }

    @Transactional
    public RequestResponse confirmReceivedQuantity(UUID requestId, ConfirmReceivedQuantityRequest request, AuthenticatedUser user) {
        WheelIntakeRequestEntity entity = requireRequest(requestId);
        requireVersion(entity, request.version());
        entity.setActualReceivedWheelQuantity(request.actualReceivedWheelQuantity());
        entity.setQuantityDiscrepancyAcknowledged(discrepancyAcknowledged(entity, request.acknowledgeDiscrepancy()));
        entity.setUpdatedBy(user.actorLabel());
        recalculationService.markCurrentAndFuturePlans();
        capacityAlertService.recalculateFromToday();
        auditService.record(null, entity.getId(), "RECEIVED_QUANTITY_CONFIRMED", user.actorLabel(),
                "Received quantity confirmed as " + request.actualReceivedWheelQuantity() + ".");
        return mapper.toRequest(requests.saveAndFlush(entity));
    }

    @Transactional
    public RequestResponse updateStatus(UUID requestId, UpdateStatusRequest request, AuthenticatedUser user) {
        WheelIntakeRequestEntity entity = requireRequest(requestId);
        requireVersion(entity, request.version());
        if (request.status() == LifecycleStatus.PICKED_UP_FROM_FACTORY) {
            entity.setActualPickupFromFactoryAt(request.actualPickupFromFactoryAt() == null
                    ? OffsetDateTime.now(clock)
                    : request.actualPickupFromFactoryAt());
        }
        changeStatus(entity, request.status(), user, request.reason());
        recalculationService.markCurrentAndFuturePlans();
        RequestResponse response = mapper.toRequest(requests.saveAndFlush(entity));
        capacityAlertService.recalculateFromToday();
        return response;
    }

    @Transactional
    public RequestResponse updatePriority(UUID requestId, PriorityRequest request, AuthenticatedUser user) {
        WheelIntakeRequestEntity entity = requireRequest(requestId);
        requireVersion(entity, request.version());
        if (request.manualPriority() != null && request.manualPriority() <= 0) {
            throw new InvalidRequestException("Manual priority must be greater than zero.");
        }
        entity.setManualPriority(request.manualPriority());
        entity.setPlanningLocked(Boolean.TRUE.equals(request.planningLocked()));
        entity.setUpdatedBy(user.actorLabel());
        recalculationService.markCurrentAndFuturePlans();
        capacityAlertService.recalculateFromToday();
        auditService.record(null, entity.getId(), "PRIORITY_UPDATED", user.actorLabel(),
                "Priority/lock changed. " + (request.reason() == null ? "" : request.reason()));
        return mapper.toRequest(requests.saveAndFlush(entity));
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> statusHistory(UUID requestId) {
        return statusHistory.findByRequestIdOrderByChangedAtAsc(requestId).stream()
                .map(mapper::toStatusHistory)
                .toList();
    }

    public List<LifecycleStatus> closedStatuses() {
        return CLOSED_STATUSES.stream().toList();
    }

    private RequestResponse persistCreated(WheelIntakeRequestEntity entity, AuthenticatedUser user) {
        WheelIntakeRequestEntity saved = requests.saveAndFlush(entity);
        recordStatus(saved, null, LifecycleStatus.REGISTERED, user.id(), user.actorLabel(), "Pedido registado.");
        recalculationService.markCurrentAndFuturePlans();
        capacityAlertService.recalculateFromToday();
        auditService.record(null, saved.getId(), "REQUEST_CREATED", user.actorLabel(), "Request created.");
        return mapper.toRequest(saved);
    }

    private WheelIntakeRequestEntity createEntity(RequestSource source, String externalSourceReference, String externalMessageId,
                                                 DriverEntity driver, CustomerReferenceEntity customer, Map<WheelType, Integer> quantities,
                                                 OffsetDateTime dropOffStart, OffsetDateTime dropOffEnd,
                                                 OffsetDateTime pickupStart, OffsetDateTime pickupEnd,
                                                 String notes, String actor) {
        int expectedQuantity = wheelQuantityService.total(quantities);
        validateRequestFields(expectedQuantity, dropOffStart, dropOffEnd, pickupStart, pickupEnd);
        WheelIntakeRequestEntity entity = new WheelIntakeRequestEntity();
        entity.setSource(source);
        entity.setExternalSourceReference(externalSourceReference);
        entity.setExternalMessageId(blankToNull(externalMessageId));
        entity.setCustomer(customer);
        entity.setCustomerExternalId(customer.getExternalId());
        entity.setCustomerNameSnapshot(customer.getName());
        entity.setDriver(driver);
        entity.replaceWheelQuantities(quantities);
        entity.setExpectedFactoryDropOffWindowStart(dropOffStart);
        entity.setExpectedFactoryDropOffWindowEnd(dropOffEnd);
        entity.setRequestedFactoryPickupWindowStart(pickupStart);
        entity.setRequestedFactoryPickupWindowEnd(pickupEnd);
        entity.setNotes(blankToNull(notes));
        entity.setLifecycleStatus(LifecycleStatus.REGISTERED);
        entity.setCreatedBy(actor);
        entity.setUpdatedBy(actor);
        return entity;
    }

    private void applyUpdate(WheelIntakeRequestEntity entity, RequestUpdateRequest update, boolean admin, String actor) {
        requireVersion(entity, update.version());
        if (update.customerId() != null) {
            CustomerReferenceEntity customer = requireCustomer(update.customerId());
            entity.setCustomer(customer);
            entity.setCustomerExternalId(customer.getExternalId());
            entity.setCustomerNameSnapshot(customer.getName());
        }
        if (update.wheelQuantities() != null || update.expectedWheelQuantity() != null) {
            entity.replaceWheelQuantities(wheelQuantityService.normalize(update.wheelQuantities(), update.expectedWheelQuantity()));
        }
        entity.setExpectedFactoryDropOffWindowStart(first(update.expectedFactoryDropOffWindowStart(), entity.getExpectedFactoryDropOffWindowStart()));
        entity.setExpectedFactoryDropOffWindowEnd(first(update.expectedFactoryDropOffWindowEnd(), entity.getExpectedFactoryDropOffWindowEnd()));
        entity.setRequestedFactoryPickupWindowStart(first(update.requestedFactoryPickupWindowStart(), entity.getRequestedFactoryPickupWindowStart()));
        entity.setRequestedFactoryPickupWindowEnd(first(update.requestedFactoryPickupWindowEnd(), entity.getRequestedFactoryPickupWindowEnd()));
        validateRequestFields(entity.getExpectedWheelQuantity(), entity.getExpectedFactoryDropOffWindowStart(),
                entity.getExpectedFactoryDropOffWindowEnd(), entity.getRequestedFactoryPickupWindowStart(),
                entity.getRequestedFactoryPickupWindowEnd());
        if (update.notes() != null) {
            entity.setNotes(blankToNull(update.notes()));
        }
        if (admin) {
            if (update.manualPriority() != null && update.manualPriority() <= 0) {
                throw new InvalidRequestException("Manual priority must be greater than zero.");
            }
            entity.setManualPriority(update.manualPriority());
            if (update.planningLocked() != null) {
                entity.setPlanningLocked(update.planningLocked());
            }
        }
        entity.setUpdatedBy(actor);
    }

    private CustomerReferenceEntity requireCustomer(UUID id) {
        return customers.findById(id).filter(CustomerReferenceEntity::isActive)
                .orElseThrow(() -> new EntityNotFoundException("Customer was not found."));
    }

    private WheelIntakeRequestEntity requireRequest(UUID id) {
        return requests.findById(id).orElseThrow(() -> new EntityNotFoundException("Request was not found."));
    }

    private void requireDriverOwnership(WheelIntakeRequestEntity entity, UUID driverId) {
        if (!entity.getDriver().getId().equals(driverId)) {
            throw new ForbiddenOperationException("Drivers can only access their own requests.");
        }
    }

    private void requireVersion(WheelIntakeRequestEntity entity, Long version) {
        if (version == null || version != entity.getVersion()) {
            throw new InvalidRequestException("OPTIMISTIC_LOCK", "Request was changed by another user.");
        }
    }

    private void changeStatus(WheelIntakeRequestEntity entity, LifecycleStatus newStatus, AuthenticatedUser user, String reason) {
        LifecycleStatus previous = entity.getLifecycleStatus();
        if (previous == newStatus) {
            return;
        }
        entity.setLifecycleStatus(newStatus);
        entity.setUpdatedBy(user.actorLabel());
        recordStatus(entity, previous, newStatus, user.id(), user.actorLabel(), reason);
        auditService.record(null, entity.getId(), "STATUS_CHANGED", user.actorLabel(),
                "Status changed from " + previous + " to " + newStatus + ".");
    }

    private void recordStatus(WheelIntakeRequestEntity entity, LifecycleStatus previous, LifecycleStatus next,
                              UUID actorId, String actorLabel, String reason) {
        statusHistory.save(RequestStatusHistoryEntity.create(
                entity,
                previous,
                next,
                OffsetDateTime.now(clock),
                actorId,
                actorLabel == null || actorLabel.isBlank() ? "SYSTEM" : actorLabel,
                blankToNull(reason)
        ));
    }

    private boolean discrepancyAcknowledged(WheelIntakeRequestEntity entity, Boolean requestedAcknowledgement) {
        Integer actual = entity.getActualReceivedWheelQuantity();
        if (actual == null || actual == entity.getExpectedWheelQuantity()) {
            return false;
        }
        return Boolean.TRUE.equals(requestedAcknowledgement);
    }

    private void validateRequestFields(int quantity, OffsetDateTime dropOffStart, OffsetDateTime dropOffEnd,
                                       OffsetDateTime pickupStart, OffsetDateTime pickupEnd) {
        if (quantity <= 0) {
            throw new InvalidRequestException("Expected quantity must be greater than zero.");
        }
        if (dropOffStart == null || dropOffEnd == null || pickupStart == null || pickupEnd == null) {
            throw new InvalidRequestException("Factory drop-off and pickup windows are required.");
        }
        if (dropOffStart.isAfter(dropOffEnd)) {
            throw new InvalidRequestException("The start of the factory drop-off window cannot be after its end.");
        }
        if (pickupStart.isAfter(pickupEnd)) {
            throw new InvalidRequestException("The start of the factory pickup window cannot be after its end.");
        }
        if (!pickupStart.isAfter(dropOffEnd)) {
            if (pickupEnd.isBefore(dropOffEnd)) {
                throw new InvalidRequestException("The factory pickup window must not finish before the expected factory drop-off window.");
            }
        }
    }

    private OffsetDateTime first(OffsetDateTime preferred, OffsetDateTime fallback) {
        return preferred == null ? fallback : preferred;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
