package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.MessagingIdentityEventType;
import pt.rucodel.productionplanning.entity.DriverEntity;
import pt.rucodel.productionplanning.entity.MessagingIdentityEntity;
import pt.rucodel.productionplanning.exception.InvalidRequestException;
import pt.rucodel.productionplanning.repository.DriverRepository;

import java.time.OffsetDateTime;

@Service
public class DriverRegistrationService {
    private final DriverRepository drivers;
    private final MessagingIdentityService identities;
    private final PublicCodeService publicCodes;

    public DriverRegistrationService(DriverRepository drivers, MessagingIdentityService identities,
                                     PublicCodeService publicCodes) {
        this.drivers = drivers;
        this.identities = identities;
        this.publicCodes = publicCodes;
    }

    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = InvalidRequestException.class)
    public DriverEntity registerFromTelegramName(MessagingIdentityEntity identity, TelegramIdentitySnapshot snapshot, String rawName) {
        if (identity.getDriver() != null) {
            return identity.getDriver();
        }
        String name = validateAndNormalizeName(rawName);
        DriverEntity driver = new DriverEntity();
        driver.setDriverCode(publicCodes.driverCode(drivers.nextDriverCodeNumber()));
        driver.setName(name);
        driver.setActive(true);
        driver.setCreatedBy("TELEGRAM");
        driver.setUpdatedBy("TELEGRAM");
        applyLegacyTelegramFields(driver, snapshot);
        DriverEntity saved = drivers.saveAndFlush(driver);
        identity.setDriver(saved);
        identities.completeOnboarding(identity);
        identities.record(identity, MessagingIdentityEventType.DRIVER_NAME_REGISTERED, "TELEGRAM", identity.getExternalUserId(), null);
        return saved;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void refreshLegacyTelegramFields(DriverEntity driver, TelegramIdentitySnapshot snapshot) {
        applyLegacyTelegramFields(driver, snapshot);
    }

    public String validateAndNormalizeName(String rawName) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.length() < 2 || name.length() > 120) {
            throw new InvalidRequestException("O nome deve ter entre 2 e 120 caracteres.");
        }
        if (name.startsWith("/")) {
            throw new InvalidRequestException("Indique o seu nome, não um comando.");
        }
        if (name.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidRequestException("O nome contém caracteres inválidos.");
        }
        if (!name.matches("[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*")) {
            throw new InvalidRequestException("O nome contém caracteres inválidos.");
        }
        return name;
    }

    private void applyLegacyTelegramFields(DriverEntity driver, TelegramIdentitySnapshot snapshot) {
        OffsetDateTime now = OffsetDateTime.now();
        driver.setTelegramUserId(snapshot.userId());
        driver.setTelegramChatId(snapshot.chatId());
        driver.setTelegramUsername(blankToNull(snapshot.username()));
        driver.setTelegramFirstName(blankToNull(snapshot.firstName()));
        driver.setTelegramLastName(blankToNull(snapshot.lastName()));
        if (driver.getTelegramLinkedAt() == null) {
            driver.setTelegramLinkedAt(now);
        }
        driver.setTelegramLastInteractionAt(now);
        driver.setUpdatedBy("TELEGRAM");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
