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
import pt.rucodel.productionplanning.domain.LifecycleStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "request_status_history",
        indexes = @Index(name = "idx_status_history_request_time", columnList = "request_id, changed_at")
)
public class RequestStatusHistoryEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private WheelIntakeRequestEntity request;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 40)
    private LifecycleStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 40)
    private LifecycleStatus newStatus;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_label", nullable = false, length = 160)
    private String actorLabel;

    @Column(name = "reason", length = 1000)
    private String reason;

    public static RequestStatusHistoryEntity create(WheelIntakeRequestEntity request, LifecycleStatus previousStatus,
                                                    LifecycleStatus newStatus, OffsetDateTime changedAt,
                                                    UUID actorUserId, String actorLabel, String reason) {
        RequestStatusHistoryEntity entity = new RequestStatusHistoryEntity();
        entity.id = UUID.randomUUID();
        entity.request = request;
        entity.previousStatus = previousStatus;
        entity.newStatus = newStatus;
        entity.changedAt = changedAt;
        entity.actorUserId = actorUserId;
        entity.actorLabel = actorLabel;
        entity.reason = reason;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public WheelIntakeRequestEntity getRequest() {
        return request;
    }

    public LifecycleStatus getPreviousStatus() {
        return previousStatus;
    }

    public LifecycleStatus getNewStatus() {
        return newStatus;
    }

    public OffsetDateTime getChangedAt() {
        return changedAt;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getActorLabel() {
        return actorLabel;
    }

    public String getReason() {
        return reason;
    }
}
