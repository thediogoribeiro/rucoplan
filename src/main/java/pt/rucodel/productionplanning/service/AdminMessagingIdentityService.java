package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.MessagingIdentityEventType;
import pt.rucodel.productionplanning.domain.MessagingIdentityOnboardingStatus;
import pt.rucodel.productionplanning.dto.AdminDriverDetailResponse;
import pt.rucodel.productionplanning.dto.IdentityLinkRequest;
import pt.rucodel.productionplanning.dto.MessagingIdentityResponse;
import pt.rucodel.productionplanning.entity.DriverEntity;
import pt.rucodel.productionplanning.entity.MessagingIdentityEntity;
import pt.rucodel.productionplanning.exception.EntityNotFoundException;
import pt.rucodel.productionplanning.exception.InvalidRequestException;
import pt.rucodel.productionplanning.repository.DriverRepository;
import pt.rucodel.productionplanning.repository.MessagingIdentityRepository;
import pt.rucodel.productionplanning.repository.WheelIntakeRequestRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class AdminMessagingIdentityService {
    private final MessagingIdentityRepository identities;
    private final DriverRepository drivers;
    private final WheelIntakeRequestRepository requests;
    private final MessagingIdentityService identityEvents;
    private final Clock clock;

    public AdminMessagingIdentityService(MessagingIdentityRepository identities,
                                         DriverRepository drivers,
                                         WheelIntakeRequestRepository requests,
                                         MessagingIdentityService identityEvents,
                                         Clock clock) {
        this.identities = identities;
        this.drivers = drivers;
        this.requests = requests;
        this.identityEvents = identityEvents;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MessagingIdentityResponse> listIdentities() {
        return identities.findAllWithDriverOrderByLastSeenAtDesc().stream().map(this::toIdentityResponse).toList();
    }

    @Transactional(readOnly = true)
    public MessagingIdentityResponse getIdentity(UUID id) {
        return toIdentityResponse(requireIdentity(id));
    }

    @Transactional
    public MessagingIdentityResponse link(UUID identityId, IdentityLinkRequest request, String actor) {
        MessagingIdentityEntity identity = requireIdentity(identityId);
        DriverEntity driver = drivers.findById(request.driverId())
                .orElseThrow(() -> new EntityNotFoundException("Driver was not found."));
        identity.setDriver(driver);
        identity.setOnboardingStatus(MessagingIdentityOnboardingStatus.COMPLETED);
        if (identity.getOnboardingCompletedAt() == null) {
            identity.setOnboardingCompletedAt(OffsetDateTime.now(clock));
        }
        identity.setUpdatedBy(actor);
        identityEvents.record(identity, MessagingIdentityEventType.IDENTITY_LINKED, "ADMIN", actor,
                request.reason() == null ? null : request.reason().trim());
        return toIdentityResponse(identity);
    }

    @Transactional
    public MessagingIdentityResponse block(UUID identityId, String actor) {
        MessagingIdentityEntity identity = requireIdentity(identityId);
        identity.setBlockedAt(OffsetDateTime.now(clock));
        identity.setOnboardingStatus(MessagingIdentityOnboardingStatus.BLOCKED);
        identity.setUpdatedBy(actor);
        identityEvents.record(identity, MessagingIdentityEventType.IDENTITY_BLOCKED, "ADMIN", actor, null);
        return toIdentityResponse(identity);
    }

    @Transactional
    public MessagingIdentityResponse reactivate(UUID identityId, String actor) {
        MessagingIdentityEntity identity = requireIdentity(identityId);
        identity.setBlockedAt(null);
        identity.setOnboardingStatus(identity.getDriver() == null
                ? MessagingIdentityOnboardingStatus.AWAITING_DRIVER_NAME
                : MessagingIdentityOnboardingStatus.COMPLETED);
        identity.setUpdatedBy(actor);
        identityEvents.record(identity, MessagingIdentityEventType.IDENTITY_REACTIVATED, "ADMIN", actor, null);
        return toIdentityResponse(identity);
    }

    @Transactional(readOnly = true)
    public AdminDriverDetailResponse driverDetail(DriverEntity driver) {
        List<MessagingIdentityResponse> linked = identities.findByDriverId(driver.getId()).stream()
                .sorted(Comparator.comparing(MessagingIdentityEntity::getLastSeenAt).reversed())
                .map(this::toIdentityResponse)
                .toList();
        List<pt.rucodel.productionplanning.entity.WheelIntakeRequestEntity> driverRequests =
                requests.findByDriverId(driver.getId(), org.springframework.data.domain.PageRequest.of(0, 1,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))).getContent();
        long requestCount = requests.countByDriverId(driver.getId());
        OffsetDateTime lastRequestAt = driverRequests.isEmpty() ? null : driverRequests.getFirst().getCreatedAt();
        return new AdminDriverDetailResponse(
                driver.getId(),
                driver.getExternalId(),
                driver.getName(),
                driver.isActive(),
                driver.getVersion(),
                requestCount,
                lastRequestAt,
                linked
        );
    }

    private MessagingIdentityEntity requireIdentity(UUID id) {
        return identities.findById(id).orElseThrow(() -> new EntityNotFoundException("Messaging identity was not found."));
    }

    private MessagingIdentityResponse toIdentityResponse(MessagingIdentityEntity identity) {
        DriverEntity driver = identity.getDriver();
        long requestCount = driver == null ? 0 : requests.countByDriverId(driver.getId());
        OffsetDateTime lastRequestAt = null;
        if (driver != null) {
            List<pt.rucodel.productionplanning.entity.WheelIntakeRequestEntity> latest =
                    requests.findByDriverId(driver.getId(), org.springframework.data.domain.PageRequest.of(0, 1,
                            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))).getContent();
            lastRequestAt = latest.isEmpty() ? null : latest.getFirst().getCreatedAt();
        }
        return new MessagingIdentityResponse(
                identity.getId(),
                driver == null ? null : driver.getId(),
                driver == null ? null : driver.getName(),
                identity.getChannel(),
                identity.getIntegrationKey(),
                identity.getExternalUserId(),
                identity.getExternalChatId(),
                identity.getExternalUsername(),
                identity.getPlatformFirstName(),
                identity.getPlatformLastName(),
                identity.getLanguageCode(),
                maskPhone(identity.getPhoneNumber()),
                identity.getFirstSeenAt(),
                identity.getLastSeenAt(),
                identity.getOnboardingStatus(),
                identity.getOnboardingCompletedAt(),
                identity.getBlockedAt(),
                requestCount,
                lastRequestAt,
                identity.getVersion()
        );
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        if (phone.length() <= 5) {
            return "***";
        }
        return phone.substring(0, Math.min(4, phone.length())) + "***" + phone.substring(phone.length() - 2);
    }
}
