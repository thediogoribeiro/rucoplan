package pt.rucodel.productionplanning.entity;

import jakarta.persistence.*;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;

import java.util.UUID;

@Entity
@Table(
        name = "production_site",
        indexes = {
                @Index(name = "idx_production_site_code", columnList = "code", unique = true),
                @Index(name = "idx_production_site_active", columnList = "active")
        }
)
public class ProductionSiteEntity extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, unique = true, length = 12, updatable = false)
    private ProductionSiteCode code;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "timezone", nullable = false, length = 80)
    private String timezone;

    @Column(name = "active", nullable = false)
    private boolean active = true;

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

    public ProductionSiteCode getCode() {
        return code;
    }

    public void setCode(ProductionSiteCode code) {
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getVersion() {
        return version;
    }
}
