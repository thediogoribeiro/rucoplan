package pt.rucodel.productionplanning.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.*;
import pt.rucodel.productionplanning.dto.*;
import pt.rucodel.productionplanning.entity.CustomerReferenceEntity;
import pt.rucodel.productionplanning.entity.CustomerRegistrationRequestEntity;
import pt.rucodel.productionplanning.entity.DriverEntity;
import pt.rucodel.productionplanning.entity.MessagingIdentityEntity;
import pt.rucodel.productionplanning.entity.ProductionSiteEntity;
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
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class WheelIntakeRequestService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WheelIntakeRequestService.class);
    private static final EnumSet<LifecycleStatus> DRIVER_EDITABLE_STATUSES = EnumSet.of(
            LifecycleStatus.COMMUNICATED
    );
    private static final EnumSet<LifecycleStatus> CLOSED_STATUSES = EnumSet.of(
            LifecycleStatus.READY_FOR_PICKUP,
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
    private final WheelQuantityService wheelQuantityService;
    private final PublicCodeService publicCodes;
    private final ProductionSiteService productionSites;
    private final ZoneId businessZone;
    private final LocalTime overnightEndTime;

    public WheelIntakeRequestService(WheelIntakeRequestRepository requests, CustomerReferenceRepository customers,
                                     DriverRepository drivers, RequestStatusHistoryRepository statusHistory,
                                     ApiMapper mapper, Clock clock, RecalculationService recalculationService,
                                     AuditService auditService, ApplicationEventPublisher eventPublisher,
                                     WheelQuantityService wheelQuantityService,
                                     PublicCodeService publicCodes,
                                     ProductionSiteService productionSites,
                                     AppProperties appProperties,
                                     @org.springframework.beans.factory.annotation.Value("${app.planning.overnight-end-time:06:00}") String overnightEndTime) {
        this.requests = requests;
        this.customers = customers;
        this.drivers = drivers;
        this.statusHistory = statusHistory;
        this.mapper = mapper;
        this.clock = clock;
        this.recalculationService = recalculationService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.wheelQuantityService = wheelQuantityService;
        this.publicCodes = publicCodes;
        this.productionSites = productionSites;
        this.businessZone = ZoneId.of(appProperties.timezone());
        this.overnightEndTime = LocalTime.parse(overnightEndTime);
    }

    @Transactional
    public RequestResponse createForDriver(RequestCreateRequest request, AuthenticatedUser user) {
        ProductionSiteEntity site = productionSites.requireByCode(user.productionSiteCode());
        DriverEntity driver = drivers.findById(user.driverId())
                .orElseThrow(() -> new EntityNotFoundException("Driver was not found."));
        requireDriverSite(driver, site);
        WheelIntakeRequestEntity entity = createEntity(
                site,
                RequestSource.WEB,
                null,
                null,
                driver,
                requireCustomer(user.productionSiteCode(), request.customerId()),
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
        ProductionSiteEntity site = productionSites.requireByCode(user.productionSiteCode());
        DriverEntity driver = drivers.findById(request.driverId())
                .orElseThrow(() -> new EntityNotFoundException("Driver was not found."));
        requireDriverSite(driver, site);
        WheelIntakeRequestEntity entity = createEntity(
                site,
                RequestSource.WEB,
                null,
                null,
                driver,
                requireCustomer(user.productionSiteCode(), request.customerId()),
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
    public RequestResponse createManual(ManualRequestCreateRequest request, AuthenticatedUser user) {
        ProductionSiteEntity site = productionSites.requireByCode(user.productionSiteCode());
        ZoneId siteZone = productionSites.zone(site);
        CustomerReferenceEntity customer = requireCustomer(user.productionSiteCode(), request.customerReferenceId());
        DriverEntity driver = request.driverId() == null ? null : drivers.findById(request.driverId())
                .orElseThrow(() -> new EntityNotFoundException("Driver was not found."));
        if (driver != null) {
            requireDriverSite(driver, site);
        }
        boolean alreadyAtFactory = Boolean.TRUE.equals(request.alreadyAtFactory());
        OffsetDateTime arrivalAt = request.actualArrivalAt();
        if (alreadyAtFactory && arrivalAt == null) {
            throw new InvalidRequestException("MANUAL_ARRIVAL_REQUIRED",
                    "A chegada real é obrigatória quando as jantes já estão na fábrica.");
        }
        if (arrivalAt != null && arrivalAt.isAfter(OffsetDateTime.now(clock).plusMinutes(5))) {
            throw new InvalidRequestException("MANUAL_ARRIVAL_IN_FUTURE",
                    "A chegada real não pode ser uma data futura.");
        }
        if (!alreadyAtFactory && (request.expectedFactoryDropoffDate() == null || request.expectedFactoryDropoffWindow() == null)) {
            throw new InvalidRequestException("MANUAL_EXPECTED_ARRIVAL_REQUIRED",
                    "A chegada prevista e o intervalo são obrigatórios quando as jantes ainda não chegaram.");
        }
        OffsetDateTime dropoffStart = alreadyAtFactory
                ? arrivalAt
                : request.expectedFactoryDropoffWindow().startAt(request.expectedFactoryDropoffDate(), siteZone);
        OffsetDateTime dropoffEnd = alreadyAtFactory
                ? arrivalAt
                : request.expectedFactoryDropoffWindow().endAt(request.expectedFactoryDropoffDate(), siteZone, overnightEndTime);
        OffsetDateTime pickupStart = request.requestedPickupWindow().startAt(request.requestedPickupDate(), siteZone);
        OffsetDateTime pickupEnd = request.requestedPickupWindow().endAt(request.requestedPickupDate(), siteZone, overnightEndTime);
        WheelIntakeRequestEntity entity = createEntity(
                site,
                RequestSource.MANUAL,
                "MANUAL_FACTORY_INTAKE",
                null,
                driver,
                customer,
                wheelQuantityService.normalize(request.wheelQuantities(), null),
                dropoffStart,
                dropoffEnd,
                pickupStart,
                pickupEnd,
                request.notes(),
                user.actorLabel()
        );
        entity.setFactoryDropoffSlot(alreadyAtFactory ? null : request.expectedFactoryDropoffWindow());
        entity.setFactoryPickupSlot(request.requestedPickupWindow());
        if (alreadyAtFactory) {
            entity.setLifecycleStatus(LifecycleStatus.AT_FACTORY);
            entity.setActualFactoryArrivalAt(arrivalAt);
            entity.setArrivalConfirmedAt(OffsetDateTime.now(clock));
            entity.setArrivalConfirmedBy(user.actorLabel());
            entity.setArrivalConfirmationSource("MANUAL_FACTORY_INTAKE");
        }
        WheelIntakeRequestEntity saved = requests.saveAndFlush(entity);
        recordStatus(saved, null, saved.getLifecycleStatus(), user.id(), user.actorLabel(),
                alreadyAtFactory ? "Pedido manual criado com chegada física confirmada." : "Pedido manual comunicado.");
        recalculationService.markCurrentAndFuturePlans(saved.getProductionSite());
        auditService.record(null, saved.getId(), "REQUEST_CREATED", user.actorLabel(), "Manual request created.");
        logCommunicatedRequest(saved);
        if (alreadyAtFactory) {
            eventPublisher.publishEvent(new WheelIntakeRequestArrivedEvent(saved.getId(), saved.getSource(), saved.getProductionSite().getCode()));
        } else {
            publishRequestCommunicated(saved);
        }
        return mapper.toRequest(saved);
    }

    @Transactional
    public WheelIntakeRequestEntity createFromIntegration(String externalMessageId, DriverEntity driver,
                                                         CustomerReferenceEntity customer, WhatsAppIntakeRequest request,
                                                         String actor) {
        ProductionSiteEntity site = requireSiteFromCustomer(customer);
        WheelIntakeRequestEntity entity = createEntity(
                site,
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
        recordStatus(saved, null, LifecycleStatus.COMMUNICATED, null, actor, "Pedido comunicado pelo agente WhatsApp.");
        recalculationService.markCurrentAndFuturePlans(saved.getProductionSite());
        auditService.record(null, saved.getId(), "REQUEST_CREATED", actor, "Request created from WhatsApp agent.");
        logCommunicatedRequest(saved);
        publishRequestCommunicated(saved);
        return saved;
    }

    @Transactional
    public WheelIntakeRequestEntity createFromWhatsApp(String externalMessageId, DriverEntity driver,
                                                       MessagingIdentityEntity submittedByIdentity,
                                                       CustomerReferenceEntity customer, Map<WheelType, Integer> quantities,
                                                       OffsetDateTime dropOffStart, OffsetDateTime dropOffEnd,
                                                       FactoryTimeSlot dropoffSlot,
                                                       OffsetDateTime pickupStart, OffsetDateTime pickupEnd,
                                                       FactoryTimeSlot pickupSlot,
                                                       String notes) {
        ProductionSiteEntity site = requireSiteFromCustomer(customer);
        String idempotencyKey = blankToNull(externalMessageId);
        if (idempotencyKey != null) {
            java.util.Optional<WheelIntakeRequestEntity> existing = requests.findByExternalMessageId(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        WheelIntakeRequestEntity entity = createEntity(
                site,
                RequestSource.WHATSAPP,
                "WHATSAPP_CLOUD_API",
                idempotencyKey,
                driver,
                customer,
                quantities,
                dropOffStart,
                dropOffEnd,
                pickupStart,
                pickupEnd,
                notes,
                "WHATSAPP"
        );
        entity.setSubmittedByIdentity(submittedByIdentity);
        entity.setFactoryDropoffSlot(dropoffSlot);
        entity.setFactoryPickupSlot(pickupSlot);
        WheelIntakeRequestEntity saved = requests.saveAndFlush(entity);
        recordStatus(saved, null, LifecycleStatus.COMMUNICATED, null, "WHATSAPP", "Pedido comunicado pelo WhatsApp.");
        recalculationService.markCurrentAndFuturePlans(saved.getProductionSite());
        auditService.record(null, saved.getId(), "REQUEST_CREATED", "WHATSAPP", "Request created from WhatsApp.");
        logCommunicatedRequest(saved);
        publishRequestCommunicated(saved);
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
        return createFromTelegramInternal(externalMessageId, driver, submittedByIdentity, customer, null, quantities,
                dropOffStart, dropOffEnd, dropoffSlot, pickupStart, pickupEnd, pickupSlot, notes);
    }

    @Transactional
    public WheelIntakeRequestEntity createFromTelegramWithPendingCustomer(String externalMessageId, DriverEntity driver,
                                                                          MessagingIdentityEntity submittedByIdentity,
                                                                          CustomerRegistrationRequestEntity pendingCustomer,
                                                                          Map<WheelType, Integer> quantities,
                                                                          OffsetDateTime dropOffStart, OffsetDateTime dropOffEnd,
                                                                          FactoryTimeSlot dropoffSlot,
                                                                          OffsetDateTime pickupStart, OffsetDateTime pickupEnd,
                                                                          FactoryTimeSlot pickupSlot,
                                                                          String notes) {
        return createFromTelegramInternal(externalMessageId, driver, submittedByIdentity, null, pendingCustomer, quantities,
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
        return createFromTelegramInternal(externalMessageId, driver, null, customer, null, quantities,
                dropOffStart, dropOffEnd, dropoffSlot, pickupStart, pickupEnd, pickupSlot, notes);
    }

    private WheelIntakeRequestEntity createFromTelegramInternal(String externalMessageId, DriverEntity driver,
                                                               MessagingIdentityEntity submittedByIdentity,
                                                               CustomerReferenceEntity customer,
                                                               CustomerRegistrationRequestEntity pendingCustomer,
                                                               Map<WheelType, Integer> quantities,
                                                               OffsetDateTime dropOffStart, OffsetDateTime dropOffEnd,
                                                               FactoryTimeSlot dropoffSlot,
                                                               OffsetDateTime pickupStart, OffsetDateTime pickupEnd,
                                                               FactoryTimeSlot pickupSlot,
                                                               String notes) {
        ProductionSiteEntity site = requireSiteFromCustomerOrPending(customer, pendingCustomer);
        String idempotencyKey = blankToNull(externalMessageId);
        if (idempotencyKey != null) {
            java.util.Optional<WheelIntakeRequestEntity> existing = requests.findByExternalMessageId(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        WheelIntakeRequestEntity entity = createEntity(
                site,
                RequestSource.TELEGRAM,
                "TELEGRAM",
                idempotencyKey,
                driver,
                customer,
                pendingCustomer,
                customer == null && pendingCustomer != null ? pendingCustomer.getProposedName() + " (Pendente de validação)" : null,
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
        recordStatus(saved, null, LifecycleStatus.COMMUNICATED, null, "TELEGRAM", "Pedido comunicado pelo bot Telegram.");
        recalculationService.markCurrentAndFuturePlans(saved.getProductionSite());
        auditService.record(null, saved.getId(), "REQUEST_CREATED", "TELEGRAM", "Request created from Telegram bot.");
        logCommunicatedRequest(saved);
        publishRequestCommunicated(saved);
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
        return listForAdmin(ProductionSiteCode.PT, driverId, customerId, status, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestResponse> listForAdmin(ProductionSiteCode siteCode, UUID driverId, UUID customerId, LifecycleStatus status, int page, int size) {
        return mapper.toPage(
                requests.searchForSite(siteCode, driverId, customerId, status,
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))),
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

    @Transactional(readOnly = true)
    public RequestResponse getForAdmin(ProductionSiteCode siteCode, UUID requestId) {
        return mapper.toRequest(requireRequest(siteCode, requestId));
    }

    @Transactional(readOnly = true)
    public List<RequestResponse> listFactoryArrivals(LifecycleStatus status) {
        return listFactoryArrivals(ProductionSiteCode.PT, status);
    }

    @Transactional(readOnly = true)
    public List<RequestResponse> listFactoryArrivals(ProductionSiteCode siteCode, LifecycleStatus status) {
        LifecycleStatus effectiveStatus = status == null ? LifecycleStatus.COMMUNICATED : status;
        if (effectiveStatus != LifecycleStatus.COMMUNICATED) {
            throw new InvalidRequestException("FACTORY_ARRIVAL_STATUS_INVALID",
                    "A Entrada na Fábrica lista apenas pedidos comunicados.");
        }
        return requests.findFactoryArrivalQueueForSite(siteCode, effectiveStatus).stream()
                .map(mapper::toRequest)
                .toList();
    }

    @Transactional
    public RequestResponse updateForDriver(UUID requestId, UUID driverId, RequestUpdateRequest update, AuthenticatedUser user) {
        WheelIntakeRequestEntity entity = requireRequest(requestId);
        requireDriverOwnership(entity, driverId);
        if (!DRIVER_EDITABLE_STATUSES.contains(entity.getLifecycleStatus()) || entity.getActualFactoryArrivalAt() != null) {
            throw new ForbiddenOperationException("Driver requests can only be edited before the wheels are confirmed at the factory.");
        }
        applyUpdate(entity, update, false, user.actorLabel());
        recalculationService.markCurrentAndFuturePlans(entity.getProductionSite());
        auditService.record(null, entity.getId(), "REQUEST_UPDATED", user.actorLabel(), "Driver updated request.");
        return mapper.toRequest(requests.saveAndFlush(entity));
    }

    @Transactional
    public RequestResponse updateForAdmin(UUID requestId, RequestUpdateRequest update, AuthenticatedUser user) {
        WheelIntakeRequestEntity entity = requireRequest(user.productionSiteCode(), requestId);
        applyUpdate(entity, update, true, user.actorLabel());
        recalculationService.markCurrentAndFuturePlans(entity.getProductionSite());
        auditService.record(null, entity.getId(), "REQUEST_UPDATED", user.actorLabel(), "Administrator updated request.");
        return mapper.toRequest(requests.saveAndFlush(entity));
    }

    @Transactional
    public RequestResponse cancelForDriver(UUID requestId, UUID driverId, RequestUpdateRequest request, AuthenticatedUser user) {
        WheelIntakeRequestEntity entity = requireRequest(requestId);
        requireDriverOwnership(entity, driverId);
        requireVersion(entity, request.version());
        if (entity.getActualFactoryArrivalAt() != null || !DRIVER_EDITABLE_STATUSES.contains(entity.getLifecycleStatus())) {
            throw new ForbiddenOperationException("Only future requests that have not arrived at the factory can be cancelled by the driver.");
        }
        if (!entity.getExpectedFactoryDropOffWindowStart().isAfter(OffsetDateTime.now(clock))) {
            throw new ForbiddenOperationException("Only future requests can be cancelled by the driver.");
        }
        changeStatus(entity, LifecycleStatus.CANCELLED, user, "Pedido cancelado pelo motorista/vendedor.");
        recalculationService.markCurrentAndFuturePlans(entity.getProductionSite());
        RequestResponse response = mapper.toRequest(requests.saveAndFlush(entity));
        return response;
    }

    @Transactional
    public RequestResponse confirmArrival(UUID requestId, ConfirmArrivalRequest request, AuthenticatedUser user) {
        WheelIntakeRequestEntity entity = user.role() == UserRole.DRIVER
                ? requireRequest(requestId)
                : requireRequest(user.productionSiteCode(), requestId);
        if (user.role() == UserRole.DRIVER) {
            requireDriverOwnership(entity, user.driverId());
        }
        if (entity.getActualFactoryArrivalAt() != null
                && EnumSet.of(LifecycleStatus.AT_FACTORY, LifecycleStatus.IN_PRODUCTION, LifecycleStatus.READY_FOR_PICKUP)
                .contains(entity.getLifecycleStatus())) {
            return mapper.toRequest(entity);
        }
        if (request.version() != null) {
            requireVersion(entity, request.version());
        }
        OffsetDateTime actualArrivalAt = request.actualFactoryArrivalAt() == null
                ? OffsetDateTime.now(clock)
                : request.actualFactoryArrivalAt();
        if (actualArrivalAt.isAfter(OffsetDateTime.now(clock).plusMinutes(5))) {
            throw new InvalidRequestException("FACTORY_ARRIVAL_IN_FUTURE",
                    "A chegada real não pode ser uma data futura.");
        }
        entity.setActualFactoryArrivalAt(actualArrivalAt);
        entity.setArrivalConfirmedAt(OffsetDateTime.now(clock));
        entity.setArrivalConfirmedBy(user.actorLabel());
        entity.setArrivalConfirmationSource(user.role() == UserRole.DRIVER ? "DRIVER" : "ADMIN");
        if (request.actualReceivedWheelQuantity() != null) {
            entity.setActualReceivedWheelQuantity(request.actualReceivedWheelQuantity());
            entity.setQuantityDiscrepancyAcknowledged(discrepancyAcknowledged(entity, request.acknowledgeDiscrepancy()));
        }
        entity.setUpdatedBy(user.actorLabel());
        if (entity.getLifecycleStatus() == LifecycleStatus.COMMUNICATED) {
            changeStatus(entity, LifecycleStatus.AT_FACTORY, user, request.reason() == null ? "Chegada física confirmada." : request.reason());
        } else {
            auditService.record(null, entity.getId(), "REQUEST_ARRIVAL_CONFIRMED", user.actorLabel(), "Factory arrival confirmed.");
        }
        recalculationService.markCurrentAndFuturePlans(entity.getProductionSite());
        WheelIntakeRequestEntity saved = requests.saveAndFlush(entity);
        eventPublisher.publishEvent(new WheelIntakeRequestArrivedEvent(saved.getId(), saved.getSource(), saved.getProductionSite().getCode()));
        return mapper.toRequest(saved);
    }

    @Transactional
    public RequestResponse confirmReceivedQuantity(UUID requestId, ConfirmReceivedQuantityRequest request, AuthenticatedUser user) {
        WheelIntakeRequestEntity entity = requireRequest(user.productionSiteCode(), requestId);
        requireVersion(entity, request.version());
        entity.setActualReceivedWheelQuantity(request.actualReceivedWheelQuantity());
        entity.setQuantityDiscrepancyAcknowledged(discrepancyAcknowledged(entity, request.acknowledgeDiscrepancy()));
        entity.setUpdatedBy(user.actorLabel());
        recalculationService.markCurrentAndFuturePlans(entity.getProductionSite());
        auditService.record(null, entity.getId(), "RECEIVED_QUANTITY_CONFIRMED", user.actorLabel(),
                "Received quantity confirmed as " + request.actualReceivedWheelQuantity() + ".");
        return mapper.toRequest(requests.saveAndFlush(entity));
    }

    @Transactional
    public RequestResponse updateStatus(UUID requestId, UpdateStatusRequest request, AuthenticatedUser user) {
        throw new ForbiddenOperationException("Alterações manuais de estado foram desativadas. Use o fluxo operacional correspondente.");
    }

    @Transactional
    public RequestResponse updatePriority(UUID requestId, PriorityRequest request, AuthenticatedUser user) {
        WheelIntakeRequestEntity entity = requireRequest(user.productionSiteCode(), requestId);
        requireVersion(entity, request.version());
        if (request.manualPriority() != null && request.manualPriority() <= 0) {
            throw new InvalidRequestException("Manual priority must be greater than zero.");
        }
        entity.setManualPriority(request.manualPriority());
        entity.setPlanningLocked(Boolean.TRUE.equals(request.planningLocked()));
        entity.setUpdatedBy(user.actorLabel());
        recalculationService.markCurrentAndFuturePlans(entity.getProductionSite());
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
        recordStatus(saved, null, LifecycleStatus.COMMUNICATED, user.id(), user.actorLabel(), "Pedido comunicado.");
        recalculationService.markCurrentAndFuturePlans(entity.getProductionSite());
        auditService.record(null, saved.getId(), "REQUEST_CREATED", user.actorLabel(), "Request created.");
        logCommunicatedRequest(saved);
        publishRequestCommunicated(saved);
        return mapper.toRequest(saved);
    }

    private void publishRequestCommunicated(WheelIntakeRequestEntity request) {
        eventPublisher.publishEvent(new WheelIntakeRequestCommunicatedEvent(request.getId(), request.getSource(), request.getProductionSite().getCode()));
    }

    private WheelIntakeRequestEntity createEntity(ProductionSiteEntity site, RequestSource source, String externalSourceReference, String externalMessageId,
                                                 DriverEntity driver, CustomerReferenceEntity customer, Map<WheelType, Integer> quantities,
                                                 OffsetDateTime dropOffStart, OffsetDateTime dropOffEnd,
                                                 OffsetDateTime pickupStart, OffsetDateTime pickupEnd,
                                                 String notes, String actor) {
        return createEntity(site, source, externalSourceReference, externalMessageId, driver, customer, null, null, quantities,
                dropOffStart, dropOffEnd, pickupStart, pickupEnd, notes, actor);
    }

    private WheelIntakeRequestEntity createEntity(ProductionSiteEntity site, RequestSource source, String externalSourceReference, String externalMessageId,
                                                 DriverEntity driver, CustomerReferenceEntity customer,
                                                 CustomerRegistrationRequestEntity pendingCustomer, String customerNameSnapshot,
                                                 Map<WheelType, Integer> quantities,
                                                 OffsetDateTime dropOffStart, OffsetDateTime dropOffEnd,
                                                 OffsetDateTime pickupStart, OffsetDateTime pickupEnd,
                                                 String notes, String actor) {
        int expectedQuantity = wheelQuantityService.total(quantities);
        validateRequestFields(expectedQuantity, dropOffStart, dropOffEnd, pickupStart, pickupEnd);
        if (customer == null && pendingCustomer == null) {
            throw new InvalidRequestException("Customer is required.");
        }
        requireCustomerSite(site, customer, pendingCustomer);
        WheelIntakeRequestEntity entity = new WheelIntakeRequestEntity();
        entity.setProductionSite(site);
        entity.setRequestCode(publicCodes.newRequestCode());
        entity.setSource(source);
        entity.setExternalSourceReference(externalSourceReference);
        entity.setExternalMessageId(blankToNull(externalMessageId));
        entity.setCustomer(customer);
        entity.setCustomerRegistrationRequest(pendingCustomer);
        entity.setCustomerExternalId(customer == null ? null : customer.getExternalId());
        entity.setCustomerNameSnapshot(customer == null ? customerNameSnapshot : customer.getName());
        entity.setDriver(driver);
        entity.replaceWheelQuantities(quantities);
        entity.setExpectedFactoryDropOffWindowStart(dropOffStart);
        entity.setExpectedFactoryDropOffWindowEnd(dropOffEnd);
        entity.setRequestedFactoryPickupWindowStart(pickupStart);
        entity.setRequestedFactoryPickupWindowEnd(pickupEnd);
        entity.setNotes(blankToNull(notes));
        entity.setLifecycleStatus(LifecycleStatus.COMMUNICATED);
        entity.setCreatedBy(actor);
        entity.setUpdatedBy(actor);
        return entity;
    }

    private void logCommunicatedRequest(WheelIntakeRequestEntity request) {
        LOGGER.info("Wheel intake request communicated requestId={} requestCode={} source={} driverId={} identityId={} totalQuantity={} availableAt={} deadlineAt={}",
                request.getId(),
                request.getRequestCode(),
                request.getSource(),
                request.getDriver() == null ? null : request.getDriver().getId(),
                request.getSubmittedByIdentity() == null ? null : request.getSubmittedByIdentity().getId(),
                request.getExpectedWheelQuantity(),
                request.getExpectedFactoryDropOffWindowEnd(),
                request.getRequestedFactoryPickupWindowStart());
    }

    private void applyUpdate(WheelIntakeRequestEntity entity, RequestUpdateRequest update, boolean admin, String actor) {
        requireVersion(entity, update.version());
        if (update.customerId() != null) {
            CustomerReferenceEntity customer = requireCustomer(entity.getProductionSite().getCode(), update.customerId());
            entity.setCustomer(customer);
            entity.setCustomerRegistrationRequest(null);
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

    private CustomerReferenceEntity requireCustomer(ProductionSiteCode siteCode, UUID id) {
        return customers.findByIdForSite(siteCode, id).filter(CustomerReferenceEntity::isActive)
                .orElseThrow(() -> new EntityNotFoundException("Customer was not found."));
    }

    private WheelIntakeRequestEntity requireRequest(UUID id) {
        return requests.findById(id).orElseThrow(() -> new EntityNotFoundException("Request was not found."));
    }

    private WheelIntakeRequestEntity requireRequest(ProductionSiteCode siteCode, UUID id) {
        return requests.findByIdForSite(siteCode, id).orElseThrow(() -> new EntityNotFoundException("Request was not found."));
    }

    private ProductionSiteEntity requireSiteFromCustomer(CustomerReferenceEntity customer) {
        if (customer == null || customer.getProductionSite() == null) {
            return productionSites.portugal();
        }
        return customer.getProductionSite();
    }

    private ProductionSiteEntity requireSiteFromCustomerOrPending(CustomerReferenceEntity customer,
                                                                 CustomerRegistrationRequestEntity pendingCustomer) {
        if (customer != null) {
            return requireSiteFromCustomer(customer);
        }
        if (pendingCustomer != null && pendingCustomer.getProductionSite() != null) {
            return pendingCustomer.getProductionSite();
        }
        return productionSites.portugal();
    }

    private void requireCustomerSite(ProductionSiteEntity site, CustomerReferenceEntity customer,
                                     CustomerRegistrationRequestEntity pendingCustomer) {
        if (customer != null && !customer.getProductionSite().getId().equals(site.getId())) {
            throw new InvalidRequestException("REQUEST_CUSTOMER_SITE_MISMATCH",
                    "O cliente não pertence à unidade de produção selecionada.");
        }
        if (pendingCustomer != null && pendingCustomer.getProductionSite() != null
                && !pendingCustomer.getProductionSite().getId().equals(site.getId())) {
            throw new InvalidRequestException("REQUEST_CUSTOMER_SITE_MISMATCH",
                    "O pedido de cliente não pertence à unidade de produção selecionada.");
        }
    }

    private void requireDriverSite(DriverEntity driver, ProductionSiteEntity site) {
        if (!productionSites.driverHasAccess(driver, site)) {
            throw new InvalidRequestException("DRIVER_SITE_MISMATCH",
                    "O motorista não está associado à unidade de produção selecionada.");
        }
    }

    private void requireDriverOwnership(WheelIntakeRequestEntity entity, UUID driverId) {
        if (entity.getDriver() == null || !entity.getDriver().getId().equals(driverId)) {
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
