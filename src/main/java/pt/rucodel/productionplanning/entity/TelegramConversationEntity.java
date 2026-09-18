package pt.rucodel.productionplanning.entity;

import jakarta.persistence.*;
import pt.rucodel.productionplanning.domain.TelegramConversationState;

import java.util.UUID;

@Entity
@Table(
        name = "telegram_conversation",
        indexes = {
                @Index(name = "idx_telegram_conversation_user", columnList = "telegram_user_id", unique = true),
                @Index(name = "idx_telegram_conversation_driver", columnList = "driver_id"),
                @Index(name = "idx_telegram_conversation_identity", columnList = "messaging_identity_id")
        }
)
public class TelegramConversationEntity extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "telegram_user_id", nullable = false, unique = true)
    private Long telegramUserId;

    @Column(name = "telegram_chat_id", nullable = false)
    private Long telegramChatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private DriverEntity driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "messaging_identity_id")
    private MessagingIdentityEntity messagingIdentity;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 60)
    private TelegramConversationState state;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_draft_id")
    private TelegramIntakeDraftEntity activeDraft;

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

    public Long getTelegramUserId() {
        return telegramUserId;
    }

    public void setTelegramUserId(Long telegramUserId) {
        this.telegramUserId = telegramUserId;
    }

    public Long getTelegramChatId() {
        return telegramChatId;
    }

    public void setTelegramChatId(Long telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public DriverEntity getDriver() {
        return driver;
    }

    public void setDriver(DriverEntity driver) {
        this.driver = driver;
    }

    public MessagingIdentityEntity getMessagingIdentity() {
        return messagingIdentity;
    }

    public void setMessagingIdentity(MessagingIdentityEntity messagingIdentity) {
        this.messagingIdentity = messagingIdentity;
    }

    public TelegramConversationState getState() {
        return state;
    }

    public void setState(TelegramConversationState state) {
        this.state = state;
    }

    public TelegramIntakeDraftEntity getActiveDraft() {
        return activeDraft;
    }

    public void setActiveDraft(TelegramIntakeDraftEntity activeDraft) {
        this.activeDraft = activeDraft;
    }

    public long getVersion() {
        return version;
    }
}
