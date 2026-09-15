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
        name = "request_wheel_quantity",
        uniqueConstraints = @UniqueConstraint(name = "uk_request_wheel_quantity_type", columnNames = {"request_id", "wheel_type"}),
        indexes = {
                @Index(name = "idx_request_wheel_quantity_request", columnList = "request_id"),
                @Index(name = "idx_request_wheel_quantity_type", columnList = "wheel_type")
        }
)
public class RequestWheelQuantityEntity extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private WheelIntakeRequestEntity request;

    @Enumerated(EnumType.STRING)
    @Column(name = "wheel_type", nullable = false, length = 40)
    private WheelType wheelType;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "completed_quantity", nullable = false)
    private int completedQuantity;

    @Override
    protected void assignIdIfNecessary() {
        if (id == null) {
            id = newId();
        }
    }

    public UUID getId() {
        return id;
    }

    public WheelIntakeRequestEntity getRequest() {
        return request;
    }

    public void setRequest(WheelIntakeRequestEntity request) {
        this.request = request;
    }

    public WheelType getWheelType() {
        return wheelType;
    }

    public void setWheelType(WheelType wheelType) {
        this.wheelType = wheelType;
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
}
