package pt.rucodel.productionplanning.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "daily_production_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_daily_settings_site_key", columnNames = {"production_site_id", "settings_key"}),
        indexes = {
                @Index(name = "idx_daily_settings_date", columnList = "settings_date"),
                @Index(name = "idx_daily_settings_site_date", columnList = "production_site_id, settings_date")
        }
)
public class DailyProductionSettingsEntity extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_site_id", nullable = false)
    private ProductionSiteEntity productionSite;

    @Column(name = "settings_key", nullable = false, length = 80)
    private String settingsKey;

    @Column(name = "settings_date")
    private LocalDate settingsDate;

    @Column(name = "daily_capacity", nullable = false)
    private int dailyCapacity;

    @Column(name = "daily_target", nullable = false)
    private int dailyTarget;

    @Column(name = "fallback_minutes_per_wheel", nullable = false)
    private int fallbackMinutesPerWheel;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "settings", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder asc")
    private List<ProductionTimeWindowEntity> timeWindows = new ArrayList<>();

    @Override
    protected void assignIdIfNecessary() {
        if (id == null) {
            id = newId();
        }
    }

    public UUID getId() {
        return id;
    }

    public ProductionSiteEntity getProductionSite() {
        return productionSite;
    }

    public void setProductionSite(ProductionSiteEntity productionSite) {
        this.productionSite = productionSite;
    }

    public String getSettingsKey() {
        return settingsKey;
    }

    public void setSettingsKey(String settingsKey) {
        this.settingsKey = settingsKey;
    }

    public LocalDate getSettingsDate() {
        return settingsDate;
    }

    public void setSettingsDate(LocalDate settingsDate) {
        this.settingsDate = settingsDate;
    }

    public int getDailyCapacity() {
        return dailyCapacity;
    }

    public void setDailyCapacity(int dailyCapacity) {
        this.dailyCapacity = dailyCapacity;
    }

    public int getDailyTarget() {
        return dailyTarget;
    }

    public void setDailyTarget(int dailyTarget) {
        this.dailyTarget = dailyTarget;
    }

    public int getFallbackMinutesPerWheel() {
        return fallbackMinutesPerWheel;
    }

    public void setFallbackMinutesPerWheel(int fallbackMinutesPerWheel) {
        this.fallbackMinutesPerWheel = fallbackMinutesPerWheel;
    }

    public long getVersion() {
        return version;
    }

    public List<ProductionTimeWindowEntity> getTimeWindows() {
        return timeWindows;
    }

    public void replaceTimeWindows(List<ProductionTimeWindowEntity> windows) {
        timeWindows.clear();
        for (ProductionTimeWindowEntity window : windows) {
            window.setSettings(this);
            timeWindows.add(window);
        }
    }
}
