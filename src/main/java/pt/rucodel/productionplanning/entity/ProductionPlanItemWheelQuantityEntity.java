package pt.rucodel.productionplanning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import pt.rucodel.productionplanning.domain.WheelType;

import java.util.UUID;

@Entity
@Table(
        name = "production_plan_item_wheel_quantity",
        uniqueConstraints = @UniqueConstraint(name = "uk_plan_item_wheel_quantity_type", columnNames = {"plan_item_id", "wheel_type"}),
        indexes = @Index(name = "idx_plan_item_wheel_quantity_item", columnList = "plan_item_id")
)
public class ProductionPlanItemWheelQuantityEntity extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_item_id", nullable = false)
    private ProductionPlanItemEntity planItem;

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

    public ProductionPlanItemEntity getPlanItem() {
        return planItem;
    }

    public void setPlanItem(ProductionPlanItemEntity planItem) {
        this.planItem = planItem;
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
