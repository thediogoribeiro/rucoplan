package pt.rucodel.productionplanning.entity;

import jakarta.persistence.*;
import pt.rucodel.productionplanning.domain.FactoryTimeSlot;
import pt.rucodel.productionplanning.domain.TelegramDraftStatus;
import pt.rucodel.productionplanning.domain.WheelType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "telegram_intake_draft",
        indexes = {
                @Index(name = "idx_telegram_draft_driver", columnList = "driver_id"),
                @Index(name = "idx_telegram_draft_status", columnList = "status")
        }
)
public class TelegramIntakeDraftEntity extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private DriverEntity driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_reference_id")
    private CustomerReferenceEntity customer;

    @Column(name = "customer_name_snapshot")
    private String customerNameSnapshot;

    @Column(name = "customer_candidate_ids", length = 2000)
    private String customerCandidateIds;

    @Column(name = "wheel_quantity")
    private Integer wheelQuantity;

    @OneToMany(mappedBy = "draft", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("wheelType ASC")
    private List<TelegramDraftWheelQuantityEntity> wheelQuantities = new ArrayList<>();

    @Column(name = "factory_dropoff_date")
    private LocalDate factoryDropoffDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "factory_dropoff_slot", length = 40)
    private FactoryTimeSlot factoryDropoffSlot;

    @Column(name = "ready_date")
    private LocalDate readyDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "factory_pickup_slot", length = 40)
    private FactoryTimeSlot factoryPickupSlot;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private TelegramDraftStatus status = TelegramDraftStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_request_id")
    private WheelIntakeRequestEntity confirmedRequest;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Override
    protected void assignIdIfNecessary() {
        if (id == null) {
            id = newId();
        }
    }

    public UUID getId() {
        return id;
    }

    public DriverEntity getDriver() {
        return driver;
    }

    public void setDriver(DriverEntity driver) {
        this.driver = driver;
    }

    public CustomerReferenceEntity getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerReferenceEntity customer) {
        this.customer = customer;
    }

    public String getCustomerNameSnapshot() {
        return customerNameSnapshot;
    }

    public void setCustomerNameSnapshot(String customerNameSnapshot) {
        this.customerNameSnapshot = customerNameSnapshot;
    }

    public String getCustomerCandidateIds() {
        return customerCandidateIds;
    }

    public void setCustomerCandidateIds(String customerCandidateIds) {
        this.customerCandidateIds = customerCandidateIds;
    }

    public Integer getWheelQuantity() {
        return wheelQuantity;
    }

    public void setWheelQuantity(Integer wheelQuantity) {
        this.wheelQuantity = wheelQuantity;
    }

    public List<TelegramDraftWheelQuantityEntity> getWheelQuantities() {
        return wheelQuantities;
    }

    public Integer wheelQuantity(WheelType type) {
        return wheelQuantities.stream()
                .filter(quantity -> quantity.getWheelType() == type)
                .map(TelegramDraftWheelQuantityEntity::getQuantity)
                .findFirst()
                .orElse(null);
    }

    public int totalWheelQuantity() {
        return wheelQuantities.stream().mapToInt(TelegramDraftWheelQuantityEntity::getQuantity).sum();
    }

    public Map<WheelType, Integer> wheelQuantityMap() {
        Map<WheelType, Integer> result = new EnumMap<>(WheelType.class);
        for (WheelType type : WheelType.values()) {
            result.put(type, wheelQuantity(type) == null ? 0 : wheelQuantity(type));
        }
        return result;
    }

    public void setWheelQuantity(WheelType type, int quantity) {
        TelegramDraftWheelQuantityEntity existing = wheelQuantities.stream()
                .filter(candidate -> candidate.getWheelType() == type)
                .findFirst()
                .orElse(null);
        if (existing == null) {
            existing = new TelegramDraftWheelQuantityEntity();
            existing.setDraft(this);
            existing.setWheelType(type);
            wheelQuantities.add(existing);
        }
        existing.setQuantity(quantity);
        wheelQuantity = totalWheelQuantity();
    }

    public void clearWheelQuantities() {
        wheelQuantities.clear();
        wheelQuantity = null;
    }

    public void clearWheelQuantity(WheelType type) {
        wheelQuantities.removeIf(quantity -> quantity.getWheelType() == type);
        wheelQuantity = wheelQuantities.isEmpty() ? null : totalWheelQuantity();
    }

    public LocalDate getFactoryDropoffDate() {
        return factoryDropoffDate;
    }

    public void setFactoryDropoffDate(LocalDate factoryDropoffDate) {
        this.factoryDropoffDate = factoryDropoffDate;
    }

    public FactoryTimeSlot getFactoryDropoffSlot() {
        return factoryDropoffSlot;
    }

    public void setFactoryDropoffSlot(FactoryTimeSlot factoryDropoffSlot) {
        this.factoryDropoffSlot = factoryDropoffSlot;
    }

    public LocalDate getReadyDate() {
        return readyDate;
    }

    public void setReadyDate(LocalDate readyDate) {
        this.readyDate = readyDate;
    }

    public FactoryTimeSlot getFactoryPickupSlot() {
        return factoryPickupSlot;
    }

    public void setFactoryPickupSlot(FactoryTimeSlot factoryPickupSlot) {
        this.factoryPickupSlot = factoryPickupSlot;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public TelegramDraftStatus getStatus() {
        return status;
    }

    public void setStatus(TelegramDraftStatus status) {
        this.status = status;
    }

    public WheelIntakeRequestEntity getConfirmedRequest() {
        return confirmedRequest;
    }

    public void setConfirmedRequest(WheelIntakeRequestEntity confirmedRequest) {
        this.confirmedRequest = confirmedRequest;
    }

    public long getVersion() {
        return version;
    }
}
