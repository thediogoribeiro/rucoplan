package pt.rucodel.productionplanning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import pt.rucodel.productionplanning.domain.GenerationTrigger;
import pt.rucodel.productionplanning.domain.ProductionPlanStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "production_plan",
        indexes = {
                @Index(name = "idx_production_plan_date_version", columnList = "planning_date, version_number"),
                @Index(name = "idx_production_plan_current", columnList = "planning_date, current_plan")
        }
)
public class ProductionPlanEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "planning_date", nullable = false)
    private LocalDate planningDate;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "current_plan", nullable = false)
    private boolean currentPlan = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ProductionPlanStatus status = ProductionPlanStatus.PUBLISHED;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_trigger", nullable = false, length = 40)
    private GenerationTrigger generationTrigger;

    @Column(name = "capacity_used", nullable = false)
    private int capacityUsed;

    @Column(name = "target_used", nullable = false)
    private int targetUsed;

    @Column(name = "minimum_target_snapshot", nullable = false)
    private int minimumTargetSnapshot;

    @Column(name = "maximum_target_snapshot", nullable = false)
    private int maximumTargetSnapshot;

    @Column(name = "total_known_wheels", nullable = false)
    private int totalKnownWheels;

    @Column(name = "total_planned", nullable = false)
    private int totalPlanned;

    @Column(name = "total_waiting_for_arrival", nullable = false)
    private int totalWaitingForArrival;

    @Column(name = "total_future_workload", nullable = false)
    private int totalFutureWorkload;

    @Column(name = "total_at_risk", nullable = false)
    private int totalAtRisk;

    @Column(name = "total_over_capacity", nullable = false)
    private int totalOverCapacity;

    @Column(name = "total_completed", nullable = false)
    private int totalCompleted;

    @Column(name = "total_remaining", nullable = false)
    private int totalRemaining;

    @Column(name = "overtime_quantity", nullable = false)
    private int overtimeQuantity;

    @Column(name = "below_minimum_quantity", nullable = false)
    private int belowMinimumQuantity;

    @Column(name = "carried_over_quantity", nullable = false)
    private int carriedOverQuantity;

    @Column(name = "advanced_quantity", nullable = false)
    private int advancedQuantity;

    @Column(name = "fallback_estimates_used", nullable = false)
    private boolean fallbackEstimatesUsed;

    @Column(name = "requires_recalculation", nullable = false)
    private boolean requiresRecalculation;

    @Column(name = "warning", length = 1000)
    private String warning;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "closed_by", length = 160)
    private String closedBy;

    @Column(name = "reopened_at")
    private OffsetDateTime reopenedAt;

    @Column(name = "reopened_by", length = 160)
    private String reopenedBy;

    @Column(name = "reopen_reason", length = 1000)
    private String reopenReason;

    @Version
    @Column(name = "optimistic_version", nullable = false)
    private long optimisticVersion;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDate getPlanningDate() {
        return planningDate;
    }

    public void setPlanningDate(LocalDate planningDate) {
        this.planningDate = planningDate;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public boolean isCurrentPlan() {
        return currentPlan;
    }

    public void setCurrentPlan(boolean currentPlan) {
        this.currentPlan = currentPlan;
    }

    public ProductionPlanStatus getStatus() {
        return status;
    }

    public void setStatus(ProductionPlanStatus status) {
        this.status = status;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(OffsetDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public GenerationTrigger getGenerationTrigger() {
        return generationTrigger;
    }

    public void setGenerationTrigger(GenerationTrigger generationTrigger) {
        this.generationTrigger = generationTrigger;
    }

    public int getCapacityUsed() {
        return capacityUsed;
    }

    public void setCapacityUsed(int capacityUsed) {
        this.capacityUsed = capacityUsed;
    }

    public int getTargetUsed() {
        return targetUsed;
    }

    public void setTargetUsed(int targetUsed) {
        this.targetUsed = targetUsed;
    }

    public int getMinimumTargetSnapshot() {
        return minimumTargetSnapshot;
    }

    public void setMinimumTargetSnapshot(int minimumTargetSnapshot) {
        this.minimumTargetSnapshot = minimumTargetSnapshot;
    }

    public int getMaximumTargetSnapshot() {
        return maximumTargetSnapshot;
    }

    public void setMaximumTargetSnapshot(int maximumTargetSnapshot) {
        this.maximumTargetSnapshot = maximumTargetSnapshot;
    }

    public int getTotalKnownWheels() {
        return totalKnownWheels;
    }

    public void setTotalKnownWheels(int totalKnownWheels) {
        this.totalKnownWheels = totalKnownWheels;
    }

    public int getTotalPlanned() {
        return totalPlanned;
    }

    public void setTotalPlanned(int totalPlanned) {
        this.totalPlanned = totalPlanned;
    }

    public int getTotalWaitingForArrival() {
        return totalWaitingForArrival;
    }

    public void setTotalWaitingForArrival(int totalWaitingForArrival) {
        this.totalWaitingForArrival = totalWaitingForArrival;
    }

    public int getTotalFutureWorkload() {
        return totalFutureWorkload;
    }

    public void setTotalFutureWorkload(int totalFutureWorkload) {
        this.totalFutureWorkload = totalFutureWorkload;
    }

    public int getTotalAtRisk() {
        return totalAtRisk;
    }

    public void setTotalAtRisk(int totalAtRisk) {
        this.totalAtRisk = totalAtRisk;
    }

    public int getTotalOverCapacity() {
        return totalOverCapacity;
    }

    public void setTotalOverCapacity(int totalOverCapacity) {
        this.totalOverCapacity = totalOverCapacity;
    }

    public int getTotalCompleted() {
        return totalCompleted;
    }

    public void setTotalCompleted(int totalCompleted) {
        this.totalCompleted = totalCompleted;
    }

    public int getTotalRemaining() {
        return totalRemaining;
    }

    public void setTotalRemaining(int totalRemaining) {
        this.totalRemaining = totalRemaining;
    }

    public int getOvertimeQuantity() {
        return overtimeQuantity;
    }

    public void setOvertimeQuantity(int overtimeQuantity) {
        this.overtimeQuantity = overtimeQuantity;
    }

    public int getBelowMinimumQuantity() {
        return belowMinimumQuantity;
    }

    public void setBelowMinimumQuantity(int belowMinimumQuantity) {
        this.belowMinimumQuantity = belowMinimumQuantity;
    }

    public int getCarriedOverQuantity() {
        return carriedOverQuantity;
    }

    public void setCarriedOverQuantity(int carriedOverQuantity) {
        this.carriedOverQuantity = carriedOverQuantity;
    }

    public int getAdvancedQuantity() {
        return advancedQuantity;
    }

    public void setAdvancedQuantity(int advancedQuantity) {
        this.advancedQuantity = advancedQuantity;
    }

    public boolean isFallbackEstimatesUsed() {
        return fallbackEstimatesUsed;
    }

    public void setFallbackEstimatesUsed(boolean fallbackEstimatesUsed) {
        this.fallbackEstimatesUsed = fallbackEstimatesUsed;
    }

    public boolean isRequiresRecalculation() {
        return requiresRecalculation;
    }

    public void setRequiresRecalculation(boolean requiresRecalculation) {
        this.requiresRecalculation = requiresRecalculation;
    }

    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(OffsetDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(String closedBy) {
        this.closedBy = closedBy;
    }

    public OffsetDateTime getReopenedAt() {
        return reopenedAt;
    }

    public void setReopenedAt(OffsetDateTime reopenedAt) {
        this.reopenedAt = reopenedAt;
    }

    public String getReopenedBy() {
        return reopenedBy;
    }

    public void setReopenedBy(String reopenedBy) {
        this.reopenedBy = reopenedBy;
    }

    public String getReopenReason() {
        return reopenReason;
    }

    public void setReopenReason(String reopenReason) {
        this.reopenReason = reopenReason;
    }

    public long getOptimisticVersion() {
        return optimisticVersion;
    }
}
