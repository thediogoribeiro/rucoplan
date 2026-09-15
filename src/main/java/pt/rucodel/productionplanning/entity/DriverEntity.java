package pt.rucodel.productionplanning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "driver",
        indexes = {
                @Index(name = "idx_driver_external_id", columnList = "external_id"),
                @Index(name = "idx_driver_active", columnList = "active"),
                @Index(name = "idx_driver_telegram_user_id", columnList = "telegram_user_id")
        }
)
public class DriverEntity extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "external_id", length = 120, unique = true)
    private String externalId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "telegram_user_id", unique = true)
    private Long telegramUserId;

    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    @Column(name = "telegram_username")
    private String telegramUsername;

    @Column(name = "telegram_first_name")
    private String telegramFirstName;

    @Column(name = "telegram_last_name")
    private String telegramLastName;

    @Column(name = "telegram_linked_at")
    private OffsetDateTime telegramLinkedAt;

    @Column(name = "telegram_last_interaction_at")
    private OffsetDateTime telegramLastInteractionAt;

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

    public void setId(UUID id) {
        this.id = id;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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

    public String getTelegramUsername() {
        return telegramUsername;
    }

    public void setTelegramUsername(String telegramUsername) {
        this.telegramUsername = telegramUsername;
    }

    public String getTelegramFirstName() {
        return telegramFirstName;
    }

    public void setTelegramFirstName(String telegramFirstName) {
        this.telegramFirstName = telegramFirstName;
    }

    public String getTelegramLastName() {
        return telegramLastName;
    }

    public void setTelegramLastName(String telegramLastName) {
        this.telegramLastName = telegramLastName;
    }

    public OffsetDateTime getTelegramLinkedAt() {
        return telegramLinkedAt;
    }

    public void setTelegramLinkedAt(OffsetDateTime telegramLinkedAt) {
        this.telegramLinkedAt = telegramLinkedAt;
    }

    public OffsetDateTime getTelegramLastInteractionAt() {
        return telegramLastInteractionAt;
    }

    public void setTelegramLastInteractionAt(OffsetDateTime telegramLastInteractionAt) {
        this.telegramLastInteractionAt = telegramLastInteractionAt;
    }

    public long getVersion() {
        return version;
    }
}
