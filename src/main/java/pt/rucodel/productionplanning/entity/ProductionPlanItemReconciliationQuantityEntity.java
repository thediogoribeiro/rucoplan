package pt.rucodel.productionplanning.entity;

import jakarta.persistence.*;
import pt.rucodel.productionplanning.domain.WheelType;

import java.util.UUID;

@Entity
@Table(
        name = "production_plan_item_reconciliation_quantity",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_item_reconciliation_quantity_type",
                columnNames = {"reconciliation_id", "wheel_type"}
        ),
        indexes = @Index(name = "idx_item_reconciliation_quantity_parent", columnList = "reconciliation_id")
)
public class ProductionPlanItemReconciliationQuantityEntity extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciliation_id", nullable = false)
    private ProductionPlanItemReconciliationEntity reconciliation;

    @Enumerated(EnumType.STRING)
    @Column(name = "wheel_type", nullable = false, length = 40)
    private WheelType wheelType;

    @Column(name = "planned_quantity", nullable = false)
    private int plannedQuantity;

    @Column(name = "completed_quantity", nullable = false)
    private int completedQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private int remainingQuantity;

    @Override
    protected void assignIdIfNecessary() {
        if (id == null) {
            id = newId();
        }
    }

    public UUID getId() {
        return id;
    }

    public ProductionPlanItemReconciliationEntity getReconciliation() {
        return reconciliation;
    }

    public void setReconciliation(ProductionPlanItemReconciliationEntity reconciliation) {
        this.reconciliation = reconciliation;
    }

    public WheelType getWheelType() {
        return wheelType;
    }

    public void setWheelType(WheelType wheelType) {
        this.wheelType = wheelType;
    }

    public int getPlannedQuantity() {
        return plannedQuantity;
    }

    public void setPlannedQuantity(int plannedQuantity) {
        this.plannedQuantity = plannedQuantity;
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
}
