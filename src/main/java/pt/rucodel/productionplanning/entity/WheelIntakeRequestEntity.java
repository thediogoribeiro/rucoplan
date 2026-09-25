package pt.rucodel.productionplanning.entity;

import jakarta.persistence.*;
import pt.rucodel.productionplanning.domain.LifecycleStatus;
import pt.rucodel.productionplanning.domain.FactoryTimeSlot;
import pt.rucodel.productionplanning.domain.RequestSource;
import pt.rucodel.productionplanning.domain.WheelType;

import java.time.OffsetDateTime;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "wheel_intake_request",
        indexes = {
                @Index(name = "idx_wheel_request_driver_status", columnList = "driver_id, lifecycle_status"),
                @Index(name = "idx_wheel_request_pickup_start", columnList = "requested_factory_pickup_start"),
                @Index(name = "idx_wheel_request_dropoff_end", columnList = "expected_factory_dropoff_end"),
                @Index(name = "idx_wheel_request_status", columnList = "lifecycle_status"),
                @Index(name = "idx_wheel_request_code", columnList = "request_code"),
                @Index(name = "idx_wheel_request_external_message", columnList = "external_message_id"),
                @Index(name = "idx_wheel_request_submitted_identity", columnList = "submitted_by_identity_id")
        }
)
public class WheelIntakeRequestEntity extends BaseEntity {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Lisbon");
    private static final int BIPARTITE_MINIMUM_BUSINESS_DAYS = 15;
    private static final String BIPARTITE_DEADLINE_REASON = "Prazo mínimo de 15 dias úteis para jantes bipartidas.";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 40)
    private RequestSource source;

    @Column(name = "external_source_reference", length = 160)
    private String externalSourceReference;

    @Column(name = "request_code", length = 15, unique = true)
    private String requestCode;

    @Column(name = "external_message_id", unique = true, length = 180)
    private String externalMessageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_reference_id")
    private CustomerReferenceEntity customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_registration_request_id")
    private CustomerRegistrationRequestEntity customerRegistrationRequest;

    @Column(name = "customer_external_id", length = 120)
    private String customerExternalId;

    @Column(name = "customer_name_snapshot", nullable = false)
    private String customerNameSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private DriverEntity driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_identity_id")
    private MessagingIdentityEntity submittedByIdentity;

    @Column(name = "expected_wheel_quantity", nullable = false)
    private int expectedWheelQuantity;

    @Column(name = "actual_received_wheel_quantity")
    private Integer actualReceivedWheelQuantity;

    @Column(name = "completed_wheel_quantity", nullable = false)
    private int completedWheelQuantity;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("wheelType ASC")
    private List<RequestWheelQuantityEntity> wheelQuantities = new ArrayList<>();

    @Column(name = "quantity_discrepancy_acknowledged", nullable = false)
    private boolean quantityDiscrepancyAcknowledged;

    @Column(name = "expected_factory_dropoff_start", nullable = false)
    private OffsetDateTime expectedFactoryDropOffWindowStart;

    @Column(name = "expected_factory_dropoff_end", nullable = false)
    private OffsetDateTime expectedFactoryDropOffWindowEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "factory_dropoff_slot", length = 40)
    private FactoryTimeSlot factoryDropoffSlot;

    @Column(name = "requested_factory_pickup_start", nullable = false)
    private OffsetDateTime requestedFactoryPickupWindowStart;

    @Column(name = "requested_factory_pickup_end", nullable = false)
    private OffsetDateTime requestedFactoryPickupWindowEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "factory_pickup_slot", length = 40)
    private FactoryTimeSlot factoryPickupSlot;

    @Column(name = "actual_factory_arrival_at")
    private OffsetDateTime actualFactoryArrivalAt;

    @Column(name = "arrival_confirmed_at")
    private OffsetDateTime arrivalConfirmedAt;

    @Column(name = "arrival_confirmed_by", length = 160)
    private String arrivalConfirmedBy;

    @Column(name = "arrival_confirmation_source", length = 80)
    private String arrivalConfirmationSource;

    @Column(name = "actual_pickup_from_factory_at")
    private OffsetDateTime actualPickupFromFactoryAt;

    @Column(name = "rucopi_production_job_id", length = 160)
    private String rucopiProductionJobId;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 40)
    private LifecycleStatus lifecycleStatus = LifecycleStatus.COMMUNICATED;

    @Column(name = "manual_priority")
    private Integer manualPriority;

    @Column(name = "planning_locked", nullable = false)
    private boolean planningLocked;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Override
    protected void assignIdIfNecessary() {
        if (id == null) {
            id = newId();
        }
    }

    @PrePersist
    @PreUpdate
    void ensureWheelQuantityRows() {
        if (wheelQuantities.isEmpty() && expectedWheelQuantity > 0) {
            replaceWheelQuantities(Map.of(WheelType.NORMAL, expectedWheelQuantity));
        }
        expectedWheelQuantity = wheelQuantities.stream().mapToInt(RequestWheelQuantityEntity::getQuantity).sum();
        completedWheelQuantity = wheelQuantities.stream().mapToInt(RequestWheelQuantityEntity::getCompletedQuantity).sum();
        refreshWheelQuantityDeadlines();
    }

    private void refreshWheelQuantityDeadlines() {
        if (requestedFactoryPickupWindowStart == null || expectedFactoryDropOffWindowEnd == null) {
            return;
        }
        OffsetDateTime available = actualFactoryArrivalAt == null ? expectedFactoryDropOffWindowEnd : actualFactoryArrivalAt;
        for (RequestWheelQuantityEntity quantity : wheelQuantities) {
            quantity.setRequestedDeadlineAt(requestedFactoryPickupWindowStart);
            if (quantity.getWheelType() == WheelType.BIPARTITE && quantity.getQuantity() > 0) {
                OffsetDateTime minimumDeadline = minimumBipartiteDeadline(available, requestedFactoryPickupWindowStart);
                if (minimumDeadline.isAfter(requestedFactoryPickupWindowStart)) {
                    quantity.setEffectiveDeadlineAt(minimumDeadline);
                    quantity.setDeadlineAdjustmentReason(BIPARTITE_DEADLINE_REASON);
                    continue;
                }
            }
            quantity.setEffectiveDeadlineAt(requestedFactoryPickupWindowStart);
            quantity.setDeadlineAdjustmentReason(null);
        }
    }

    private OffsetDateTime minimumBipartiteDeadline(OffsetDateTime available, OffsetDateTime requestedDeadline) {
        LocalDate date = available.atZoneSameInstant(BUSINESS_ZONE).toLocalDate();
        int remaining = BIPARTITE_MINIMUM_BUSINESS_DAYS;
        while (remaining > 0) {
            date = date.plusDays(1);
            if (isBusinessDay(date)) {
                remaining--;
            }
        }
        return date.atTime(requestedDeadline.atZoneSameInstant(BUSINESS_ZONE).toLocalTime())
                .atZone(BUSINESS_ZONE)
                .toOffsetDateTime();
    }

    private boolean isBusinessDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public RequestSource getSource() {
        return source;
    }

    public void setSource(RequestSource source) {
        this.source = source;
    }

    public String getExternalSourceReference() {
        return externalSourceReference;
    }

    public void setExternalSourceReference(String externalSourceReference) {
        this.externalSourceReference = externalSourceReference;
    }

    public String getRequestCode() {
        return requestCode;
    }

    public void setRequestCode(String requestCode) {
        this.requestCode = requestCode;
    }

    public String getExternalMessageId() {
        return externalMessageId;
    }

    public void setExternalMessageId(String externalMessageId) {
        this.externalMessageId = externalMessageId;
    }

    public CustomerReferenceEntity getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerReferenceEntity customer) {
        this.customer = customer;
    }

    public CustomerRegistrationRequestEntity getCustomerRegistrationRequest() {
        return customerRegistrationRequest;
    }

    public void setCustomerRegistrationRequest(CustomerRegistrationRequestEntity customerRegistrationRequest) {
        this.customerRegistrationRequest = customerRegistrationRequest;
    }

    public String getCustomerExternalId() {
        return customerExternalId;
    }

    public void setCustomerExternalId(String customerExternalId) {
        this.customerExternalId = customerExternalId;
    }

    public String getCustomerNameSnapshot() {
        return customerNameSnapshot;
    }

    public void setCustomerNameSnapshot(String customerNameSnapshot) {
        this.customerNameSnapshot = customerNameSnapshot;
    }

    public DriverEntity getDriver() {
        return driver;
    }

    public void setDriver(DriverEntity driver) {
        this.driver = driver;
    }

    public MessagingIdentityEntity getSubmittedByIdentity() {
        return submittedByIdentity;
    }

    public void setSubmittedByIdentity(MessagingIdentityEntity submittedByIdentity) {
        this.submittedByIdentity = submittedByIdentity;
    }

    public int getExpectedWheelQuantity() {
        return expectedWheelQuantity;
    }

    public void setExpectedWheelQuantity(int expectedWheelQuantity) {
        this.expectedWheelQuantity = expectedWheelQuantity;
    }

    public Integer getActualReceivedWheelQuantity() {
        return actualReceivedWheelQuantity;
    }

    public void setActualReceivedWheelQuantity(Integer actualReceivedWheelQuantity) {
        this.actualReceivedWheelQuantity = actualReceivedWheelQuantity;
    }

    public int getCompletedWheelQuantity() {
        return completedWheelQuantity;
    }

    public void setCompletedWheelQuantity(int completedWheelQuantity) {
        this.completedWheelQuantity = completedWheelQuantity;
    }

    public List<RequestWheelQuantityEntity> getWheelQuantities() {
        return wheelQuantities;
    }

    public int wheelQuantity(WheelType type) {
        return wheelQuantities.stream()
                .filter(quantity -> quantity.getWheelType() == type)
                .mapToInt(RequestWheelQuantityEntity::getQuantity)
                .findFirst()
                .orElse(0);
    }

    public int completedWheelQuantity(WheelType type) {
        return wheelQuantities.stream()
                .filter(quantity -> quantity.getWheelType() == type)
                .mapToInt(RequestWheelQuantityEntity::getCompletedQuantity)
                .findFirst()
                .orElse(0);
    }

    public Map<WheelType, Integer> wheelQuantityMap() {
        Map<WheelType, Integer> result = new EnumMap<>(WheelType.class);
        for (WheelType type : WheelType.values()) {
            result.put(type, wheelQuantity(type));
        }
        return result;
    }

    public void replaceWheelQuantities(Map<WheelType, Integer> quantities) {
        wheelQuantities.clear();
        for (WheelType type : WheelType.values()) {
            RequestWheelQuantityEntity entity = new RequestWheelQuantityEntity();
            entity.setRequest(this);
            entity.setWheelType(type);
            entity.setQuantity(Math.max(quantities.getOrDefault(type, 0), 0));
            entity.setCompletedQuantity(0);
            wheelQuantities.add(entity);
        }
        expectedWheelQuantity = wheelQuantities.stream().mapToInt(RequestWheelQuantityEntity::getQuantity).sum();
    }

    public void addCompletedWheelQuantities(Map<WheelType, Integer> completedByType) {
        Map<WheelType, RequestWheelQuantityEntity> byType = new EnumMap<>(WheelType.class);
        for (RequestWheelQuantityEntity quantity : wheelQuantities) {
            byType.put(quantity.getWheelType(), quantity);
        }
        for (WheelType type : WheelType.values()) {
            RequestWheelQuantityEntity quantity = byType.get(type);
            if (quantity == null) {
                quantity = new RequestWheelQuantityEntity();
                quantity.setRequest(this);
                quantity.setWheelType(type);
                quantity.setQuantity(0);
                wheelQuantities.add(quantity);
            }
            int completed = Math.max(completedByType.getOrDefault(type, 0), 0);
            quantity.setCompletedQuantity(Math.min(quantity.getQuantity(), quantity.getCompletedQuantity() + completed));
        }
        completedWheelQuantity = wheelQuantities.stream().mapToInt(RequestWheelQuantityEntity::getCompletedQuantity).sum();
    }

    public boolean isQuantityDiscrepancyAcknowledged() {
        return quantityDiscrepancyAcknowledged;
    }

    public void setQuantityDiscrepancyAcknowledged(boolean quantityDiscrepancyAcknowledged) {
        this.quantityDiscrepancyAcknowledged = quantityDiscrepancyAcknowledged;
    }

    public OffsetDateTime getExpectedFactoryDropOffWindowStart() {
        return expectedFactoryDropOffWindowStart;
    }

    public void setExpectedFactoryDropOffWindowStart(OffsetDateTime expectedFactoryDropOffWindowStart) {
        this.expectedFactoryDropOffWindowStart = expectedFactoryDropOffWindowStart;
    }

    public OffsetDateTime getExpectedFactoryDropOffWindowEnd() {
        return expectedFactoryDropOffWindowEnd;
    }

    public void setExpectedFactoryDropOffWindowEnd(OffsetDateTime expectedFactoryDropOffWindowEnd) {
        this.expectedFactoryDropOffWindowEnd = expectedFactoryDropOffWindowEnd;
    }

    public FactoryTimeSlot getFactoryDropoffSlot() {
        return factoryDropoffSlot;
    }

    public void setFactoryDropoffSlot(FactoryTimeSlot factoryDropoffSlot) {
        this.factoryDropoffSlot = factoryDropoffSlot;
    }

    public OffsetDateTime getRequestedFactoryPickupWindowStart() {
        return requestedFactoryPickupWindowStart;
    }

    public void setRequestedFactoryPickupWindowStart(OffsetDateTime requestedFactoryPickupWindowStart) {
        this.requestedFactoryPickupWindowStart = requestedFactoryPickupWindowStart;
    }

    public OffsetDateTime getRequestedFactoryPickupWindowEnd() {
        return requestedFactoryPickupWindowEnd;
    }

    public void setRequestedFactoryPickupWindowEnd(OffsetDateTime requestedFactoryPickupWindowEnd) {
        this.requestedFactoryPickupWindowEnd = requestedFactoryPickupWindowEnd;
    }

    public FactoryTimeSlot getFactoryPickupSlot() {
        return factoryPickupSlot;
    }

    public void setFactoryPickupSlot(FactoryTimeSlot factoryPickupSlot) {
        this.factoryPickupSlot = factoryPickupSlot;
    }

    public OffsetDateTime getActualFactoryArrivalAt() {
        return actualFactoryArrivalAt;
    }

    public void setActualFactoryArrivalAt(OffsetDateTime actualFactoryArrivalAt) {
        this.actualFactoryArrivalAt = actualFactoryArrivalAt;
    }

    public OffsetDateTime getArrivalConfirmedAt() {
        return arrivalConfirmedAt;
    }

    public void setArrivalConfirmedAt(OffsetDateTime arrivalConfirmedAt) {
        this.arrivalConfirmedAt = arrivalConfirmedAt;
    }

    public String getArrivalConfirmedBy() {
        return arrivalConfirmedBy;
    }

    public void setArrivalConfirmedBy(String arrivalConfirmedBy) {
        this.arrivalConfirmedBy = arrivalConfirmedBy;
    }

    public String getArrivalConfirmationSource() {
        return arrivalConfirmationSource;
    }

    public void setArrivalConfirmationSource(String arrivalConfirmationSource) {
        this.arrivalConfirmationSource = arrivalConfirmationSource;
    }

    public OffsetDateTime getActualPickupFromFactoryAt() {
        return actualPickupFromFactoryAt;
    }

    public void setActualPickupFromFactoryAt(OffsetDateTime actualPickupFromFactoryAt) {
        this.actualPickupFromFactoryAt = actualPickupFromFactoryAt;
    }

    public String getRucopiProductionJobId() {
        return rucopiProductionJobId;
    }

    public void setRucopiProductionJobId(String rucopiProductionJobId) {
        this.rucopiProductionJobId = rucopiProductionJobId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(LifecycleStatus lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public Integer getManualPriority() {
        return manualPriority;
    }

    public void setManualPriority(Integer manualPriority) {
        this.manualPriority = manualPriority;
    }

    public boolean isPlanningLocked() {
        return planningLocked;
    }

    public void setPlanningLocked(boolean planningLocked) {
        this.planningLocked = planningLocked;
    }

    public long getVersion() {
        return version;
    }
}
