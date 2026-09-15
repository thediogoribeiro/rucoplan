package pt.rucodel.productionplanning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "planning_audit_event",
        indexes = {
                @Index(name = "idx_planning_audit_created", columnList = "created_at"),
                @Index(name = "idx_planning_audit_request", columnList = "request_id"),
                @Index(name = "idx_planning_audit_plan", columnList = "plan_id")
        }
)
public class PlanningAuditEventEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "actor", nullable = false, length = 160)
    private String actor;

    @Column(name = "detail", nullable = false, length = 2000)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public void setPlanId(UUID planId) {
        this.planId = planId;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
