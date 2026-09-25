package pt.rucodel.productionplanning.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "production_target_configuration",
        indexes = @Index(name = "idx_target_configuration_effective_from", columnList = "effective_from")
)
public class ProductionTargetConfigurationEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "minimum_daily_target", nullable = false)
    private int minimumDailyTarget;

    @Column(name = "regular_daily_capacity", nullable = false)
    private int regularDailyCapacity;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "created_by", nullable = false, length = 160)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Transient
    private boolean systemDefault;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public int getMinimumDailyTarget() {
        return minimumDailyTarget;
    }

    public void setMinimumDailyTarget(int minimumDailyTarget) {
        this.minimumDailyTarget = minimumDailyTarget;
    }

    public int getRegularDailyCapacity() {
        return regularDailyCapacity;
    }

    public void setRegularDailyCapacity(int regularDailyCapacity) {
        this.regularDailyCapacity = regularDailyCapacity;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isSystemDefault() {
        return systemDefault;
    }

    public void markSystemDefault() {
        this.systemDefault = true;
    }
}
