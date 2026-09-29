package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.CapacityAlertStatus;
import pt.rucodel.productionplanning.domain.CapacityAlertType;
import pt.rucodel.productionplanning.domain.LifecycleStatus;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.dto.CapacityAlertResponse;
import pt.rucodel.productionplanning.entity.CapacityAlertEntity;
import pt.rucodel.productionplanning.entity.DailyProductionSettingsEntity;
import pt.rucodel.productionplanning.entity.ProductionPlanEntity;
import pt.rucodel.productionplanning.entity.ProductionPlanItemEntity;
import pt.rucodel.productionplanning.entity.ProductionSiteEntity;
import pt.rucodel.productionplanning.entity.WheelIntakeRequestEntity;
import pt.rucodel.productionplanning.repository.CapacityAlertRepository;
import pt.rucodel.productionplanning.repository.DailyProductionSettingsRepository;
import pt.rucodel.productionplanning.repository.WheelIntakeRequestRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CapacityAlertService {
    private static final EnumSet<LifecycleStatus> CLOSED_STATUSES = EnumSet.of(
            LifecycleStatus.READY_FOR_PICKUP,
            LifecycleStatus.CANCELLED
    );
    private static final EnumSet<CapacityAlertStatus> OPEN_STATUSES = EnumSet.of(
            CapacityAlertStatus.ACTIVE,
            CapacityAlertStatus.ACKNOWLEDGED
    );

    private final CapacityAlertRepository alerts;
    private final WheelIntakeRequestRepository requests;
    private final DailyProductionSettingsRepository settings;
    private final ProductionSiteService productionSites;
    private final Clock clock;
    private final ZoneId businessZone;

    public CapacityAlertService(CapacityAlertRepository alerts, WheelIntakeRequestRepository requests,
                                DailyProductionSettingsRepository settings, Clock clock,
                                AppProperties appProperties,
                                ProductionSiteService productionSites) {
        this.alerts = alerts;
        this.requests = requests;
        this.settings = settings;
        this.productionSites = productionSites;
        this.clock = clock;
        this.businessZone = ZoneId.of(appProperties.timezone());
    }

    @Transactional(readOnly = true)
    public List<CapacityAlertResponse> listOpen() {
        return listOpen(ProductionSiteCode.PT);
    }

    @Transactional(readOnly = true)
    public List<CapacityAlertResponse> listOpen(ProductionSiteCode siteCode) {
        return alerts.findByProductionSite_CodeAndStatusInOrderByAffectedDateAscCreatedAtAsc(siteCode, OPEN_STATUSES).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CapacityAlertResponse acknowledge(UUID id) {
        return acknowledge(ProductionSiteCode.PT, id);
    }

    @Transactional
    public CapacityAlertResponse acknowledge(ProductionSiteCode siteCode, UUID id) {
        CapacityAlertEntity entity = alerts.findByProductionSite_CodeAndId(siteCode, id)
                .orElseThrow(() -> new pt.rucodel.productionplanning.exception.EntityNotFoundException("Capacity alert was not found."));
        if (entity.getStatus() == CapacityAlertStatus.ACTIVE) {
            entity.setStatus(CapacityAlertStatus.ACKNOWLEDGED);
        }
        return toResponse(alerts.saveAndFlush(entity));
    }

    @Transactional
    public void recalculateFromToday() {
        recalculate(ProductionSiteCode.PT, LocalDate.now(clock.withZone(businessZone)));
    }

    @Transactional
    public void recalculate(LocalDate fromDate) {
        recalculate(ProductionSiteCode.PT, fromDate);
    }

    @Transactional
    public void recalculate(ProductionSiteCode siteCode, LocalDate fromDate) {
        ProductionSiteEntity site = productionSites.requireByCode(siteCode);
        ZoneId siteZone = ZoneId.of(site.getTimezone());
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<WheelIntakeRequestEntity> openRequests = requests.findOpenRequestsForPlanningForSite(siteCode, CLOSED_STATUSES);
        Map<LocalDate, List<WheelIntakeRequestEntity>> requestsByDueDate = openRequests.stream()
                .filter(request -> !request.getRequestedFactoryPickupWindowStart().atZoneSameInstant(siteZone).toLocalDate().isBefore(fromDate))
                .collect(Collectors.groupingBy(request -> request.getRequestedFactoryPickupWindowStart().atZoneSameInstant(siteZone).toLocalDate()));

        Set<LocalDate> datesToCheck = new HashSet<>(requestsByDueDate.keySet());
        alerts.findByProductionSite_CodeAndStatusInOrderByAffectedDateAscCreatedAtAsc(siteCode, OPEN_STATUSES).stream()
                .map(CapacityAlertEntity::getAffectedDate)
                .filter(date -> !date.isBefore(fromDate))
                .forEach(datesToCheck::add);

        for (LocalDate date : datesToCheck) {
            List<WheelIntakeRequestEntity> dueRequests = requestsByDueDate.getOrDefault(date, List.of());
            int required = dueRequests.stream().mapToInt(this::quantityForPlanning).sum();
            int capacity = effectiveCapacity(siteCode, date);
            int deficit = Math.max(required - capacity, 0);
            Optional<CapacityAlertEntity> existing = alerts.findFirstByProductionSite_CodeAndTypeAndAffectedDateAndStatusIn(
                    siteCode,
                    CapacityAlertType.OVERTIME_REQUIRED,
                    date,
                    OPEN_STATUSES
            );
            if (deficit > 0) {
                CapacityAlertEntity alert = existing.orElseGet(CapacityAlertEntity::new);
                alert.setProductionSite(site);
                alert.setType(CapacityAlertType.OVERTIME_REQUIRED);
                alert.setStatus(existing.map(CapacityAlertEntity::getStatus).orElse(CapacityAlertStatus.ACTIVE));
                alert.setAffectedDate(date);
                alert.setRequiredQuantity(required);
                alert.setAvailableCapacity(capacity);
                alert.setDeficit(deficit);
                alert.setAffectedRequestIds(dueRequests.stream()
                        .map(request -> request.getId().toString())
                        .sorted()
                        .collect(Collectors.joining(",")));
                alert.setMessage("Horas extra necessárias: existem " + required
                        + " jantes que têm de estar prontas até " + date
                        + ", mas a capacidade configurada é de " + capacity
                        + ". Défice estimado: " + deficit + " jantes.");
                alerts.save(alert);
            } else {
                existing.ifPresent(alert -> {
                    alert.setStatus(CapacityAlertStatus.RESOLVED);
                    alert.setResolvedAt(now);
                    alerts.save(alert);
                });
            }
        }
    }

    @Transactional
    public void upsertFromPlan(ProductionPlanEntity plan, List<ProductionPlanItemEntity> items) {
        int deficit = Math.max(plan.getTotalPlanned() - plan.getMaximumTargetSnapshot(), 0);
        ProductionSiteCode siteCode = plan.getProductionSite().getCode();
        Optional<CapacityAlertEntity> existing = alerts.findFirstByProductionSite_CodeAndTypeAndAffectedDateAndStatusIn(
                siteCode,
                CapacityAlertType.OVERTIME_REQUIRED,
                plan.getPlanningDate(),
                OPEN_STATUSES
        );
        if (deficit > 0) {
            CapacityAlertEntity alert = existing.orElseGet(CapacityAlertEntity::new);
            alert.setProductionSite(plan.getProductionSite());
            alert.setType(CapacityAlertType.OVERTIME_REQUIRED);
            alert.setStatus(existing.map(CapacityAlertEntity::getStatus).orElse(CapacityAlertStatus.ACTIVE));
            alert.setAffectedDate(plan.getPlanningDate());
            alert.setRequiredQuantity(plan.getTotalPlanned());
            alert.setAvailableCapacity(plan.getMaximumTargetSnapshot());
            alert.setDeficit(deficit);
            alert.setAffectedRequestIds(items.stream()
                    .map(item -> item.getRequest().getId().toString())
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(",")));
            alert.setMessage("Horas extra necessárias para " + plan.getPlanningDate()
                    + ": estão planeadas " + plan.getTotalPlanned()
                    + " jantes para uma capacidade regular de " + plan.getMaximumTargetSnapshot()
                    + ". Excesso estimado: " + deficit + " jantes.");
            alerts.save(alert);
        } else {
            existing.ifPresent(alert -> {
                alert.setStatus(CapacityAlertStatus.RESOLVED);
                alert.setResolvedAt(OffsetDateTime.now(clock));
                alerts.save(alert);
            });
        }
    }

    private int effectiveCapacity(ProductionSiteCode siteCode, LocalDate date) {
        return settings.findByProductionSite_CodeAndSettingsDate(siteCode, date)
                .or(() -> settings.findByProductionSite_CodeAndSettingsKey(siteCode, ProductionSettingsService.DEFAULT_KEY))
                .map(DailyProductionSettingsEntity::getDailyCapacity)
                .orElse(40);
    }

    private int quantityForPlanning(WheelIntakeRequestEntity request) {
        return request.getActualReceivedWheelQuantity() == null
                ? request.getExpectedWheelQuantity()
                : request.getActualReceivedWheelQuantity();
    }

    private CapacityAlertResponse toResponse(CapacityAlertEntity entity) {
        List<UUID> affectedIds = Arrays.stream(entity.getAffectedRequestIds().split(","))
                .filter(value -> !value.isBlank())
                .map(UUID::fromString)
                .toList();
        return new CapacityAlertResponse(
                entity.getId(),
                entity.getType(),
                entity.getStatus(),
                entity.getAffectedDate(),
                entity.getRequiredQuantity(),
                entity.getAvailableCapacity(),
                entity.getDeficit(),
                affectedIds,
                entity.getMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getResolvedAt(),
                entity.getVersion()
        );
    }
}
