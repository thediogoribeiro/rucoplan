package pt.rucodel.productionplanning.entity;

import jakarta.persistence.*;
import pt.rucodel.productionplanning.domain.UserRole;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(
        name = "application_user_site",
        uniqueConstraints = @UniqueConstraint(name = "uk_application_user_site", columnNames = {"user_id", "production_site_id"}),
        indexes = {
                @Index(name = "idx_application_user_site_user", columnList = "user_id"),
                @Index(name = "idx_application_user_site_site", columnList = "production_site_id")
        }
)
public class ApplicationUserSiteEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private ApplicationUserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_site_id", nullable = false)
    private ProductionSiteEntity productionSite;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 30)
    private UserRole role;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy = "SYSTEM";

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
        if (createdBy == null || createdBy.isBlank()) {
            createdBy = "SYSTEM";
        }
    }

    public UUID getId() {
        return id;
    }

    public ApplicationUserEntity getUser() {
        return user;
    }

    public void setUser(ApplicationUserEntity user) {
        this.user = user;
    }

    public ProductionSiteEntity getProductionSite() {
        return productionSite;
    }

    public void setProductionSite(ProductionSiteEntity productionSite) {
        this.productionSite = productionSite;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
