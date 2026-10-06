package pt.rucodel.productionplanning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.MessagingChannel;
import pt.rucodel.productionplanning.domain.MessagingIdentityEventType;
import pt.rucodel.productionplanning.domain.MessagingIdentityOnboardingStatus;
import pt.rucodel.productionplanning.entity.MessagingIdentityEntity;
import pt.rucodel.productionplanning.entity.MessagingIdentityEventEntity;
import pt.rucodel.productionplanning.repository.MessagingIdentityEventRepository;
import pt.rucodel.productionplanning.repository.MessagingIdentityRepository;
import pt.rucodel.productionplanning.telegram.TelegramProperties;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
public class MessagingIdentityService {
    public static final String DEFAULT_TELEGRAM_INTEGRATION_KEY = "RUCODEL_TELEGRAM_BOT";
    public static final String DEFAULT_WHATSAPP_INTEGRATION_KEY = "RUCODEL_WHATSAPP_CLOUD_API";

    private final MessagingIdentityRepository identities;
    private final MessagingIdentityEventRepository events;
    private final Clock clock;
    private final String telegramIntegrationKey;
    private final String whatsappIntegrationKey;

    public MessagingIdentityService(MessagingIdentityRepository identities,
                                    MessagingIdentityEventRepository events,
                                    Clock clock,
                                    TelegramProperties telegramProperties,
                                    Environment environment,
                                    @Value("${app.integrations.whatsapp.phone-number-id:}") String whatsappPhoneNumberId) {
        this.identities = identities;
        this.events = events;
        this.clock = clock;
        String telegramBotUsername = telegramProperties.botUsername(environment.getProperty("app.environment", "local"));
        this.telegramIntegrationKey = telegramBotUsername == null || telegramBotUsername.isBlank()
                ? DEFAULT_TELEGRAM_INTEGRATION_KEY
                : telegramBotUsername.trim();
        this.whatsappIntegrationKey = whatsappPhoneNumberId == null || whatsappPhoneNumberId.isBlank()
                ? DEFAULT_WHATSAPP_INTEGRATION_KEY
                : whatsappPhoneNumberId.trim();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MessagingIdentityEntity resolveTelegramIdentity(TelegramIdentitySnapshot snapshot) {
        String externalUserId = String.valueOf(snapshot.userId());
        OffsetDateTime now = OffsetDateTime.now(clock);
        MessagingIdentityEntity identity = identities.findWithLockByChannelAndIntegrationKeyAndExternalUserId(
                        MessagingChannel.TELEGRAM, telegramIntegrationKey, externalUserId)
                .orElseGet(() -> createTelegramIdentity(snapshot, now));

        boolean profileChanged = updateTelegramMutableFields(identity, snapshot, now);
        if (profileChanged) {
            record(identity, MessagingIdentityEventType.PROFILE_UPDATED, "TELEGRAM", externalUserId, null);
        }
        return identity;
    }

    private MessagingIdentityEntity createTelegramIdentity(TelegramIdentitySnapshot snapshot, OffsetDateTime now) {
        MessagingIdentityEntity identity = new MessagingIdentityEntity();
        identity.setChannel(MessagingChannel.TELEGRAM);
        identity.setIntegrationKey(telegramIntegrationKey);
        identity.setExternalUserId(String.valueOf(snapshot.userId()));
        identity.setExternalChatId(String.valueOf(snapshot.chatId()));
        identity.setFirstSeenAt(now);
        identity.setLastSeenAt(now);
        identity.setOnboardingStatus(MessagingIdentityOnboardingStatus.AWAITING_DRIVER_NAME);
        identity.setCreatedBy("TELEGRAM");
        identity.setUpdatedBy("TELEGRAM");
        updateTelegramMutableFields(identity, snapshot, now);
        MessagingIdentityEntity saved = identities.saveAndFlush(identity);
        record(saved, MessagingIdentityEventType.FIRST_SEEN, "TELEGRAM", identity.getExternalUserId(), null);
        record(saved, MessagingIdentityEventType.ONBOARDING_STARTED, "TELEGRAM", identity.getExternalUserId(), null);
        return saved;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MessagingIdentityEntity resolveWhatsAppIdentity(WhatsAppIdentitySnapshot snapshot) {
        String externalUserId = snapshot.waId();
        OffsetDateTime now = OffsetDateTime.now(clock);
        MessagingIdentityEntity identity = identities.findWithLockByChannelAndIntegrationKeyAndExternalUserId(
                        MessagingChannel.WHATSAPP, whatsappIntegrationKey, externalUserId)
                .orElseGet(() -> createWhatsAppIdentity(snapshot, now));

        boolean profileChanged = updateWhatsAppMutableFields(identity, snapshot, now);
        if (profileChanged) {
            record(identity, MessagingIdentityEventType.PROFILE_UPDATED, "WHATSAPP", mask(externalUserId), null);
        }
        return identity;
    }

    private MessagingIdentityEntity createWhatsAppIdentity(WhatsAppIdentitySnapshot snapshot, OffsetDateTime now) {
        MessagingIdentityEntity identity = new MessagingIdentityEntity();
        identity.setChannel(MessagingChannel.WHATSAPP);
        identity.setIntegrationKey(whatsappIntegrationKey);
        identity.setExternalUserId(snapshot.waId());
        identity.setExternalChatId(snapshot.waId());
        identity.setFirstSeenAt(now);
        identity.setLastSeenAt(now);
        identity.setOnboardingStatus(MessagingIdentityOnboardingStatus.AWAITING_DRIVER_NAME);
        identity.setCreatedBy("WHATSAPP");
        identity.setUpdatedBy("WHATSAPP");
        updateWhatsAppMutableFields(identity, snapshot, now);
        MessagingIdentityEntity saved = identities.saveAndFlush(identity);
        record(saved, MessagingIdentityEventType.FIRST_SEEN, "WHATSAPP", mask(identity.getExternalUserId()), null);
        record(saved, MessagingIdentityEventType.ONBOARDING_STARTED, "WHATSAPP", mask(identity.getExternalUserId()), null);
        return saved;
    }

    private boolean updateWhatsAppMutableFields(MessagingIdentityEntity identity, WhatsAppIdentitySnapshot snapshot, OffsetDateTime now) {
        boolean changed = false;
        changed |= setIfChanged(identity.getExternalChatId(), snapshot.waId(), identity::setExternalChatId);
        changed |= setIfChanged(identity.getPhoneNumber(), blankToNull(snapshot.phoneNumber()), identity::setPhoneNumber);
        changed |= setIfChanged(identity.getPlatformFirstName(), blankToNull(snapshot.profileName()), identity::setPlatformFirstName);
        identity.setLastSeenAt(now);
        identity.setUpdatedBy("WHATSAPP");
        return changed;
    }

    private boolean updateTelegramMutableFields(MessagingIdentityEntity identity, TelegramIdentitySnapshot snapshot, OffsetDateTime now) {
        boolean changed = false;
        changed |= setIfChanged(identity.getExternalChatId(), String.valueOf(snapshot.chatId()), identity::setExternalChatId);
        changed |= setIfChanged(identity.getExternalUsername(), blankToNull(snapshot.username()), identity::setExternalUsername);
        changed |= setIfChanged(identity.getPlatformFirstName(), blankToNull(snapshot.firstName()), identity::setPlatformFirstName);
        changed |= setIfChanged(identity.getPlatformLastName(), blankToNull(snapshot.lastName()), identity::setPlatformLastName);
        changed |= setIfChanged(identity.getLanguageCode(), blankToNull(snapshot.languageCode()), identity::setLanguageCode);
        identity.setLastSeenAt(now);
        identity.setUpdatedBy("TELEGRAM");
        return changed;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completeOnboarding(MessagingIdentityEntity identity) {
        identity.setOnboardingStatus(MessagingIdentityOnboardingStatus.COMPLETED);
        identity.setOnboardingCompletedAt(OffsetDateTime.now(clock));
        String actor = identity.getChannel() == MessagingChannel.WHATSAPP ? "WHATSAPP" : "TELEGRAM";
        identity.setUpdatedBy(actor);
        record(identity, MessagingIdentityEventType.ONBOARDING_COMPLETED, actor, safeActorId(identity), null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(MessagingIdentityEntity identity, MessagingIdentityEventType type, String actorType, String actorId, String metadata) {
        MessagingIdentityEventEntity event = new MessagingIdentityEventEntity();
        event.setMessagingIdentity(identity);
        event.setEventType(type);
        event.setOccurredAt(OffsetDateTime.now(clock));
        event.setActorType(actorType);
        event.setActorId(actorId);
        event.setMetadata(metadata);
        events.save(event);
    }

    public String telegramIntegrationKey() {
        return telegramIntegrationKey;
    }

    public String whatsappIntegrationKey() {
        return whatsappIntegrationKey;
    }

    private String safeActorId(MessagingIdentityEntity identity) {
        return identity.getChannel() == MessagingChannel.WHATSAPP ? mask(identity.getExternalUserId()) : identity.getExternalUserId();
    }

    private String mask(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }

    private boolean setIfChanged(String current, String next, java.util.function.Consumer<String> setter) {
        if (java.util.Objects.equals(current, next)) {
            return false;
        }
        setter.accept(next);
        return true;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
