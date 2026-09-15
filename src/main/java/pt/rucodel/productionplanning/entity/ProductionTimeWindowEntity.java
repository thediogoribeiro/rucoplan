package pt.rucodel.productionplanning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "production_time_window")
public class ProductionTimeWindowEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settings_id", nullable = false)
    private DailyProductionSettingsEntity settings;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Column(name = "cutoff_time", nullable = false)
    private LocalTime cutoffTime;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public UUID getId() {
        return id;
    }

    public DailyProductionSettingsEntity getSettings() {
        return settings;
    }

    public void setSettings(DailyProductionSettingsEntity settings) {
        this.settings = settings;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public LocalTime getCutoffTime() {
        return cutoffTime;
    }

    public void setCutoffTime(LocalTime cutoffTime) {
        this.cutoffTime = cutoffTime;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
