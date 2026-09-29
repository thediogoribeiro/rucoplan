package pt.rucodel.productionplanning.entity;

import jakarta.persistence.*;
import pt.rucodel.productionplanning.domain.DriverProductionSiteAssociationSource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(
        name = "driver_production_site",
        uniqueConstraints = @UniqueConstraint(name = "uk_driver_production_site", columnNames = {"driver_id", "production_site_id"}),
        indexes = {
                @Index(name = "idx_driver_site_driver", columnList = "driver_id"),
                @Index(name = "idx_driver_site_site", columnList = "production_site_id")
        }
)
public class DriverProductionSiteEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private DriverEntity driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_site_id", nullable = false)
    private ProductionSiteEntity productionSite;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "association_source", nullable = false, length = 40)
    private DriverProductionSiteAssociationSource associationSource = DriverProductionSiteAssociationSource.ADMIN;

    @Column(name = "associated_at", nullable = false)
    private OffsetDateTime associatedAt;

    @Column(name = "associated_by", nullable = false, length = 160)
    private String associatedBy = "SYSTEM";

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (associatedAt == null) {
            associatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
        if (associatedBy == null || associatedBy.isBlank()) {
            associatedBy = "SYSTEM";
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

    public ProductionSiteEntity getProductionSite() {
        return productionSite;
    }

    public void setProductionSite(ProductionSiteEntity productionSite) {
        this.productionSite = productionSite;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public DriverProductionSiteAssociationSource getAssociationSource() {
        return associationSource;
    }

    public void setAssociationSource(DriverProductionSiteAssociationSource associationSource) {
        this.associationSource = associationSource;
    }

    public OffsetDateTime getAssociatedAt() {
        return associatedAt;
    }

    public String getAssociatedBy() {
        return associatedBy;
    }

    public void setAssociatedBy(String associatedBy) {
        this.associatedBy = associatedBy;
    }
}
