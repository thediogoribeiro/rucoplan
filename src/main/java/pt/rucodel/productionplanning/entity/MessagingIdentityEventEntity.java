package pt.rucodel.productionplanning.entity;

import jakarta.persistence.*;
import pt.rucodel.productionplanning.domain.MessagingIdentityEventType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "messaging_identity_event",
        indexes = {
                @Index(name = "idx_messaging_identity_event_identity", columnList = "messaging_identity_id"),
                @Index(name = "idx_messaging_identity_event_type", columnList = "event_type"),
                @Index(name = "idx_messaging_identity_event_occurred", columnList = "occurred_at")
        }
)
public class MessagingIdentityEventEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "messaging_identity_id", nullable = false)
    private MessagingIdentityEntity messagingIdentity;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    private MessagingIdentityEventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "actor_type", nullable = false, length = 40)
    private String actorType;

    @Column(name = "actor_id", length = 160)
    private String actorId;

    @Column(name = "metadata", length = 1000)
    private String metadata;

    public UUID getId() {
        return id;
    }

    public MessagingIdentityEntity getMessagingIdentity() {
        return messagingIdentity;
    }

    public void setMessagingIdentity(MessagingIdentityEntity messagingIdentity) {
        this.messagingIdentity = messagingIdentity;
    }

    public MessagingIdentityEventType getEventType() {
        return eventType;
    }

    public void setEventType(MessagingIdentityEventType eventType) {
        this.eventType = eventType;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
