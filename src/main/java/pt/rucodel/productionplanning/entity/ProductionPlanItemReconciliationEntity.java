package pt.rucodel.productionplanning.entity;

import jakarta.persistence.*;
import pt.rucodel.productionplanning.domain.ProductionPlanItemReconciliationStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "production_plan_item_reconciliation",
        indexes = {
                @Index(name = "idx_item_reconciliation_site_date", columnList = "production_site_id, planning_date"),
                @Index(name = "idx_item_reconciliation_plan", columnList = "production_plan_id"),
                @Index(name = "idx_item_reconciliation_item", columnList = "production_plan_item_id"),
                @Index(name = "idx_item_reconciliation_request", columnList = "request_id"),
                @Index(name = "idx_item_reconciliation_date", columnList = "planning_date")
        }
)
public class ProductionPlanItemReconciliationEntity extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_site_id", nullable = false)
    private ProductionSiteEntity productionSite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_plan_id", nullable = false)
    private ProductionPlanEntity productionPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_plan_item_id", nullable = false)
    private ProductionPlanItemEntity productionPlanItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private WheelIntakeRequestEntity request;

    @Column(name = "planning_date", nullable = false)
    private LocalDate planningDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ProductionPlanItemReconciliationStatus status;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "closed_by", length = 160)
    private String closedBy;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "reopened_by", length = 160)
    private String reopenedBy;

    @Column(name = "reopened_at")
    private OffsetDateTime reopenedAt;

    @Column(name = "reopen_reason", length = 1000)
    private String reopenReason;

    @Column(name = "reverted", nullable = false)
    private boolean reverted;

    @OneToMany(mappedBy = "reconciliation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("wheelType ASC")
    private List<ProductionPlanItemReconciliationQuantityEntity> quantities = new ArrayList<>();

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

    public ProductionSiteEntity getProductionSite() {
        return productionSite;
    }

    public void setProductionSite(ProductionSiteEntity productionSite) {
        this.productionSite = productionSite;
    }

    public ProductionPlanEntity getProductionPlan() {
        return productionPlan;
    }

    public void setProductionPlan(ProductionPlanEntity productionPlan) {
        this.productionPlan = productionPlan;
    }

    public ProductionPlanItemEntity getProductionPlanItem() {
        return productionPlanItem;
    }

    public void setProductionPlanItem(ProductionPlanItemEntity productionPlanItem) {
        this.productionPlanItem = productionPlanItem;
    }

    public WheelIntakeRequestEntity getRequest() {
        return request;
    }

    public void setRequest(WheelIntakeRequestEntity request) {
        this.request = request;
    }

    public LocalDate getPlanningDate() {
        return planningDate;
    }

    public void setPlanningDate(LocalDate planningDate) {
        this.planningDate = planningDate;
    }

    public ProductionPlanItemReconciliationStatus getStatus() {
        return status;
    }

    public void setStatus(ProductionPlanItemReconciliationStatus status) {
        this.status = status;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(String closedBy) {
        this.closedBy = closedBy;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(OffsetDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public String getReopenedBy() {
        return reopenedBy;
    }

    public void setReopenedBy(String reopenedBy) {
        this.reopenedBy = reopenedBy;
    }

    public OffsetDateTime getReopenedAt() {
        return reopenedAt;
    }

    public void setReopenedAt(OffsetDateTime reopenedAt) {
        this.reopenedAt = reopenedAt;
    }

    public String getReopenReason() {
        return reopenReason;
    }

    public void setReopenReason(String reopenReason) {
        this.reopenReason = reopenReason;
    }

    public boolean isReverted() {
        return reverted;
    }

    public void setReverted(boolean reverted) {
        this.reverted = reverted;
    }

    public List<ProductionPlanItemReconciliationQuantityEntity> getQuantities() {
        return quantities;
    }

    public long getVersion() {
        return version;
    }
}
