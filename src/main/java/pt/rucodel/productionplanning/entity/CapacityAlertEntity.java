package pt.rucodel.productionplanning.entity;

import jakarta.persistence.*;
import pt.rucodel.productionplanning.domain.CapacityAlertStatus;
import pt.rucodel.productionplanning.domain.CapacityAlertType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "capacity_alert",
        indexes = {
                @Index(name = "idx_capacity_alert_status", columnList = "status"),
                @Index(name = "idx_capacity_alert_affected_date", columnList = "affected_date")
        }
)
public class CapacityAlertEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private CapacityAlertType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CapacityAlertStatus status;

    @Column(name = "affected_date", nullable = false)
    private LocalDate affectedDate;

    @Column(name = "required_quantity", nullable = false)
    private int requiredQuantity;

    @Column(name = "available_capacity", nullable = false)
    private int availableCapacity;

    @Column(name = "deficit", nullable = false)
    private int deficit;

    @Column(name = "affected_request_ids", nullable = false, length = 4000)
    private String affectedRequestIds;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public CapacityAlertType getType() {
        return type;
    }

    public void setType(CapacityAlertType type) {
        this.type = type;
    }

    public CapacityAlertStatus getStatus() {
        return status;
    }

    public void setStatus(CapacityAlertStatus status) {
        this.status = status;
    }

    public LocalDate getAffectedDate() {
        return affectedDate;
    }

    public void setAffectedDate(LocalDate affectedDate) {
        this.affectedDate = affectedDate;
    }

    public int getRequiredQuantity() {
        return requiredQuantity;
    }

    public void setRequiredQuantity(int requiredQuantity) {
        this.requiredQuantity = requiredQuantity;
    }

    public int getAvailableCapacity() {
        return availableCapacity;
    }

    public void setAvailableCapacity(int availableCapacity) {
        this.availableCapacity = availableCapacity;
    }

    public int getDeficit() {
        return deficit;
    }

    public void setDeficit(int deficit) {
        this.deficit = deficit;
    }

    public String getAffectedRequestIds() {
        return affectedRequestIds;
    }

    public void setAffectedRequestIds(String affectedRequestIds) {
        this.affectedRequestIds = affectedRequestIds;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public long getVersion() {
        return version;
    }
}
