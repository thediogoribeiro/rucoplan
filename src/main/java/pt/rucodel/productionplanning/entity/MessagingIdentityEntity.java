package pt.rucodel.productionplanning.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import pt.rucodel.productionplanning.domain.MessagingChannel;
import pt.rucodel.productionplanning.domain.MessagingIdentityOnboardingStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "messaging_identity",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_messaging_identity_external_user", columnNames = {"channel", "integration_key", "external_user_id"})
        },
        indexes = {
                @Index(name = "idx_messaging_identity_driver", columnList = "driver_id"),
                @Index(name = "idx_messaging_identity_channel", columnList = "channel"),
                @Index(name = "idx_messaging_identity_onboarding", columnList = "onboarding_status"),
                @Index(name = "idx_messaging_identity_last_seen", columnList = "last_seen_at"),
                @Index(name = "idx_messaging_identity_username", columnList = "external_username")
        }
)
public class MessagingIdentityEntity extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private DriverEntity driver;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 40)
    private MessagingChannel channel;

    @Column(name = "integration_key", nullable = false, length = 120)
    private String integrationKey;

    @Column(name = "external_user_id", nullable = false, length = 160)
    private String externalUserId;

    @Column(name = "external_chat_id", length = 160)
    private String externalChatId;

    @Column(name = "external_username")
    private String externalUsername;

    @Column(name = "platform_first_name")
    private String platformFirstName;

    @Column(name = "platform_last_name")
    private String platformLastName;

    @Column(name = "language_code", length = 20)
    private String languageCode;

    @Column(name = "phone_number", length = 40)
    private String phoneNumber;

    @Column(name = "first_seen_at", nullable = false)
    private OffsetDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_status", nullable = false, length = 40)
    private MessagingIdentityOnboardingStatus onboardingStatus;

    @Column(name = "onboarding_completed_at")
    private OffsetDateTime onboardingCompletedAt;

    @Column(name = "blocked_at")
    private OffsetDateTime blockedAt;

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

    public DriverEntity getDriver() {
        return driver;
    }

    public void setDriver(DriverEntity driver) {
        this.driver = driver;
    }

    public MessagingChannel getChannel() {
        return channel;
    }

    public void setChannel(MessagingChannel channel) {
        this.channel = channel;
    }

    public String getIntegrationKey() {
        return integrationKey;
    }

    public void setIntegrationKey(String integrationKey) {
        this.integrationKey = integrationKey;
    }

    public String getExternalUserId() {
        return externalUserId;
    }

    public void setExternalUserId(String externalUserId) {
        this.externalUserId = externalUserId;
    }

    public String getExternalChatId() {
        return externalChatId;
    }

    public void setExternalChatId(String externalChatId) {
        this.externalChatId = externalChatId;
    }

    public String getExternalUsername() {
        return externalUsername;
    }

    public void setExternalUsername(String externalUsername) {
        this.externalUsername = externalUsername;
    }

    public String getPlatformFirstName() {
        return platformFirstName;
    }

    public void setPlatformFirstName(String platformFirstName) {
        this.platformFirstName = platformFirstName;
    }

    public String getPlatformLastName() {
        return platformLastName;
    }

    public void setPlatformLastName(String platformLastName) {
        this.platformLastName = platformLastName;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public OffsetDateTime getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(OffsetDateTime firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public OffsetDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(OffsetDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public MessagingIdentityOnboardingStatus getOnboardingStatus() {
        return onboardingStatus;
    }

    public void setOnboardingStatus(MessagingIdentityOnboardingStatus onboardingStatus) {
        this.onboardingStatus = onboardingStatus;
    }

    public OffsetDateTime getOnboardingCompletedAt() {
        return onboardingCompletedAt;
    }

    public void setOnboardingCompletedAt(OffsetDateTime onboardingCompletedAt) {
        this.onboardingCompletedAt = onboardingCompletedAt;
    }

    public OffsetDateTime getBlockedAt() {
        return blockedAt;
    }

    public void setBlockedAt(OffsetDateTime blockedAt) {
        this.blockedAt = blockedAt;
    }

    public long getVersion() {
        return version;
    }
}
