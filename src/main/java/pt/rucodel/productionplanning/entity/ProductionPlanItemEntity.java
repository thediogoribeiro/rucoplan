package pt.rucodel.productionplanning.entity;

import jakarta.persistence.*;
import pt.rucodel.productionplanning.domain.AvailabilityClassification;
import pt.rucodel.productionplanning.domain.ProductionPlanLineStatus;
import pt.rucodel.productionplanning.domain.RiskClassification;
import pt.rucodel.productionplanning.domain.WheelType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "production_plan_item",
        indexes = {
                @Index(name = "idx_plan_item_plan", columnList = "plan_id"),
                @Index(name = "idx_plan_item_request", columnList = "request_id"),
                @Index(name = "idx_plan_item_risk", columnList = "risk_classification")
        }
)
public class ProductionPlanItemEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private ProductionPlanEntity plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private WheelIntakeRequestEntity request;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "driver_name", nullable = false)
    private String driverName;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "completed_quantity", nullable = false)
    private int completedQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private int remainingQuantity;

    @Column(name = "request_total_quantity", nullable = false)
    private int requestTotalQuantity;

    @Column(name = "request_remaining_quantity", nullable = false)
    private int requestRemainingQuantity;

    @OneToMany(mappedBy = "planItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("wheelType ASC")
    private List<ProductionPlanItemWheelQuantityEntity> wheelQuantities = new ArrayList<>();

    @Column(name = "availability_at")
    private OffsetDateTime availabilityAt;

    @Column(name = "required_ready_at")
    private OffsetDateTime requiredReadyAt;

    @Column(name = "assigned_production_date")
    private LocalDate assignedProductionDate;

    @Column(name = "assigned_window_label", length = 120)
    private String assignedWindowLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_classification", nullable = false, length = 40)
    private AvailabilityClassification availabilityClassification;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_classification", nullable = false, length = 40)
    private RiskClassification riskClassification;

    @Column(name = "priority_score", nullable = false, precision = 12, scale = 2)
    private BigDecimal priorityScore;

    @Column(name = "priority_explanation", nullable = false, length = 1500)
    private String priorityExplanation;

    @Column(name = "manually_prioritised", nullable = false)
    private boolean manuallyPrioritised;

    @Column(name = "locked", nullable = false)
    private boolean locked;

    @Column(name = "carried_over", nullable = false)
    private boolean carriedOver;

    @Column(name = "advanced_from_future", nullable = false)
    private boolean advancedFromFuture;

    @Column(name = "operational_notes", length = 1000)
    private String operationalNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_status", nullable = false, length = 40)
    private ProductionPlanLineStatus lineStatus = ProductionPlanLineStatus.PLANNED;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @jakarta.persistence.Version
    @Column(name = "version", nullable = false)
    private long version;

    public UUID getId() {
        return id;
    }

    public ProductionPlanEntity getPlan() {
        return plan;
    }

    public void setPlan(ProductionPlanEntity plan) {
        this.plan = plan;
    }

    public WheelIntakeRequestEntity getRequest() {
        return request;
    }

    public void setRequest(WheelIntakeRequestEntity request) {
        this.request = request;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getCompletedQuantity() {
        return completedQuantity;
    }

    public void setCompletedQuantity(int completedQuantity) {
        this.completedQuantity = completedQuantity;
    }

    public int getRemainingQuantity() {
        return remainingQuantity;
    }

    public void setRemainingQuantity(int remainingQuantity) {
        this.remainingQuantity = remainingQuantity;
    }

    public int getRequestTotalQuantity() {
        return requestTotalQuantity;
    }

    public void setRequestTotalQuantity(int requestTotalQuantity) {
        this.requestTotalQuantity = requestTotalQuantity;
    }

    public int getRequestRemainingQuantity() {
        return requestRemainingQuantity;
    }

    public void setRequestRemainingQuantity(int requestRemainingQuantity) {
        this.requestRemainingQuantity = requestRemainingQuantity;
    }

    public List<ProductionPlanItemWheelQuantityEntity> getWheelQuantities() {
        return wheelQuantities;
    }

    public Map<WheelType, Integer> plannedWheelQuantityMap() {
        Map<WheelType, Integer> result = new EnumMap<>(WheelType.class);
        for (WheelType type : WheelType.values()) {
            result.put(type, wheelQuantities.stream()
                    .filter(quantity -> quantity.getWheelType() == type)
                    .mapToInt(ProductionPlanItemWheelQuantityEntity::getPlannedQuantity)
                    .findFirst()
                    .orElse(0));
        }
        return result;
    }

    public void replaceWheelQuantities(Map<WheelType, Integer> plannedByType) {
        wheelQuantities.clear();
        for (WheelType type : WheelType.values()) {
            ProductionPlanItemWheelQuantityEntity entity = new ProductionPlanItemWheelQuantityEntity();
            entity.setPlanItem(this);
            entity.setWheelType(type);
            int planned = Math.max(plannedByType.getOrDefault(type, 0), 0);
            entity.setPlannedQuantity(planned);
            entity.setCompletedQuantity(0);
            entity.setRemainingQuantity(planned);
            wheelQuantities.add(entity);
        }
        quantity = wheelQuantities.stream().mapToInt(ProductionPlanItemWheelQuantityEntity::getPlannedQuantity).sum();
        completedQuantity = wheelQuantities.stream().mapToInt(ProductionPlanItemWheelQuantityEntity::getCompletedQuantity).sum();
        remainingQuantity = wheelQuantities.stream().mapToInt(ProductionPlanItemWheelQuantityEntity::getRemainingQuantity).sum();
    }

    public void applyWheelQuantityReconciliation(Map<WheelType, Integer> completedByType, Map<WheelType, Integer> remainingByType) {
        Map<WheelType, ProductionPlanItemWheelQuantityEntity> byType = new EnumMap<>(WheelType.class);
        for (ProductionPlanItemWheelQuantityEntity quantity : wheelQuantities) {
            byType.put(quantity.getWheelType(), quantity);
        }
        for (WheelType type : WheelType.values()) {
            ProductionPlanItemWheelQuantityEntity quantity = byType.get(type);
            if (quantity == null) {
                quantity = new ProductionPlanItemWheelQuantityEntity();
                quantity.setPlanItem(this);
                quantity.setWheelType(type);
                quantity.setPlannedQuantity(0);
                wheelQuantities.add(quantity);
            }
            quantity.setCompletedQuantity(Math.max(completedByType.getOrDefault(type, 0), 0));
            quantity.setRemainingQuantity(Math.max(remainingByType.getOrDefault(type, 0), 0));
        }
        completedQuantity = wheelQuantities.stream().mapToInt(ProductionPlanItemWheelQuantityEntity::getCompletedQuantity).sum();
        remainingQuantity = wheelQuantities.stream().mapToInt(ProductionPlanItemWheelQuantityEntity::getRemainingQuantity).sum();
    }

    public OffsetDateTime getAvailabilityAt() {
        return availabilityAt;
    }

    public void setAvailabilityAt(OffsetDateTime availabilityAt) {
        this.availabilityAt = availabilityAt;
    }

    public OffsetDateTime getRequiredReadyAt() {
        return requiredReadyAt;
    }

    public void setRequiredReadyAt(OffsetDateTime requiredReadyAt) {
        this.requiredReadyAt = requiredReadyAt;
    }

    public LocalDate getAssignedProductionDate() {
        return assignedProductionDate;
    }

    public void setAssignedProductionDate(LocalDate assignedProductionDate) {
        this.assignedProductionDate = assignedProductionDate;
    }

    public String getAssignedWindowLabel() {
        return assignedWindowLabel;
    }

    public void setAssignedWindowLabel(String assignedWindowLabel) {
        this.assignedWindowLabel = assignedWindowLabel;
    }

    public AvailabilityClassification getAvailabilityClassification() {
        return availabilityClassification;
    }

    public void setAvailabilityClassification(AvailabilityClassification availabilityClassification) {
        this.availabilityClassification = availabilityClassification;
    }

    public RiskClassification getRiskClassification() {
        return riskClassification;
    }

    public void setRiskClassification(RiskClassification riskClassification) {
        this.riskClassification = riskClassification;
    }

    public BigDecimal getPriorityScore() {
        return priorityScore;
    }

    public void setPriorityScore(BigDecimal priorityScore) {
        this.priorityScore = priorityScore;
    }

    public String getPriorityExplanation() {
        return priorityExplanation;
    }

    public void setPriorityExplanation(String priorityExplanation) {
        this.priorityExplanation = priorityExplanation;
    }

    public boolean isManuallyPrioritised() {
        return manuallyPrioritised;
    }

    public void setManuallyPrioritised(boolean manuallyPrioritised) {
        this.manuallyPrioritised = manuallyPrioritised;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isCarriedOver() {
        return carriedOver;
    }

    public void setCarriedOver(boolean carriedOver) {
        this.carriedOver = carriedOver;
    }

    public boolean isAdvancedFromFuture() {
        return advancedFromFuture;
    }

    public void setAdvancedFromFuture(boolean advancedFromFuture) {
        this.advancedFromFuture = advancedFromFuture;
    }

    public String getOperationalNotes() {
        return operationalNotes;
    }

    public void setOperationalNotes(String operationalNotes) {
        this.operationalNotes = operationalNotes;
    }

    public ProductionPlanLineStatus getLineStatus() {
        return lineStatus;
    }

    public void setLineStatus(ProductionPlanLineStatus lineStatus) {
        this.lineStatus = lineStatus;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public long getVersion() {
        return version;
    }
}
