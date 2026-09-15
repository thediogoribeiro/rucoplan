package pt.rucodel.productionplanning.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import pt.rucodel.productionplanning.domain.LifecycleStatus;
import pt.rucodel.productionplanning.domain.RequestSource;
import pt.rucodel.productionplanning.domain.UserRole;
import pt.rucodel.productionplanning.entity.*;
import pt.rucodel.productionplanning.repository.*;

import java.time.*;
import java.util.List;

@Configuration
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DevSeedDataConfig {
    @Bean
    CommandLineRunner seedDevelopmentData(DriverRepository drivers,
                                          ApplicationUserRepository users,
                                          CustomerReferenceRepository customers,
                                          WheelIntakeRequestRepository requests,
                                          RequestStatusHistoryRepository history,
                                          DailyProductionSettingsRepository settings,
                                          PasswordEncoder passwordEncoder,
                                          Clock clock) {
        return args -> {
            if (users.existsByUsername("admin")) {
                return;
            }
            ZoneId zone = ZoneId.of("Europe/Lisbon");
            LocalDate today = LocalDate.now(clock.withZone(zone));

            DriverEntity d1 = driver("D001", "João Martins");
            DriverEntity d2 = driver("D002", "Marta Silva");
            DriverEntity d3 = driver("D003", "Rui Costa");
            drivers.saveAll(List.of(d1, d2, d3));

            users.save(admin("admin", "Administrador", "admin123", passwordEncoder));
            users.save(driverUser("driver1", d1, "driver123", passwordEncoder));
            users.save(driverUser("driver2", d2, "driver123", passwordEncoder));
            users.save(driverUser("driver3", d3, "driver123", passwordEncoder));

            CustomerReferenceEntity c1 = customer("C1001", "Oficina Central Braga");
            CustomerReferenceEntity c2 = customer("C1002", "Auto Reparadora Norte");
            CustomerReferenceEntity c3 = customer("C1003", "Pneus Atlântico");
            CustomerReferenceEntity c4 = customer("C1004", "Jantes e Companhia");
            customers.saveAll(List.of(c1, c2, c3, c4));

            DailyProductionSettingsEntity daily = new DailyProductionSettingsEntity();
            daily.setSettingsKey("DATE:" + today);
            daily.setSettingsDate(today);
            daily.setDailyCapacity(20);
            daily.setDailyTarget(16);
            daily.setFallbackMinutesPerWheel(20);
            daily.setCreatedBy("DEV_SEED");
            daily.setUpdatedBy("DEV_SEED");
            daily.replaceTimeWindows(List.of(
                    window("Fim da manhã", LocalTime.of(12, 30), 1),
                    window("Meio da tarde", LocalTime.of(15, 30), 2),
                    window("Fim do dia", LocalTime.of(18, 30), 3)
            ));
            settings.save(daily);

            List<WheelIntakeRequestEntity> seededRequests = List.of(
                    request(d1, c1, 4, today, "08:00", "09:00", today, "16:30", "17:30",
                            LifecycleStatus.ARRIVED_AT_FACTORY, "Pedido já na fábrica.", "07:50", null, null, false),
                    request(d2, c2, 6, today, "13:00", "14:00", today, "17:30", "18:30",
                            LifecycleStatus.REGISTERED, "Chegada prevista à hora de almoço.", null, null, null, false),
                    request(d3, c3, 5, today, "20:00", "21:00", today.plusDays(1), "09:00", "10:00",
                            LifecycleStatus.REGISTERED, "Chegada no fim do dia; normalmente passa para amanhã.", null, null, null, false),
                    request(d1, c4, 7, today, "07:30", "08:00", today, "10:30", "11:00",
                            LifecycleStatus.ARRIVED_AT_FACTORY, "Prazo apertado para demonstrar risco.", "07:35", null, null, false),
                    request(d2, c1, 18, today, "09:00", "10:00", today, "18:00", "19:00",
                            LifecycleStatus.REGISTERED, "Pedido grande para demonstrar excesso de capacidade.", null, 1, null, false),
                    request(d3, c2, 3, today.minusDays(1), "15:00", "16:00", today, "15:00", "16:00",
                            LifecycleStatus.IN_PRODUCTION, "Quantidade recebida diferente da prevista.", "16:10", null, 2, false)
            );
            requests.saveAll(seededRequests);
            for (WheelIntakeRequestEntity seeded : seededRequests) {
                history.save(RequestStatusHistoryEntity.create(seeded, null, seeded.getLifecycleStatus(),
                        OffsetDateTime.now(clock), null, "DEV_SEED", "Seed development status."));
            }
        };
    }

    private DriverEntity driver(String externalId, String name) {
        DriverEntity entity = new DriverEntity();
        entity.setExternalId(externalId);
        entity.setName(name);
        entity.setActive(true);
        entity.setCreatedBy("DEV_SEED");
        entity.setUpdatedBy("DEV_SEED");
        return entity;
    }

    private ApplicationUserEntity admin(String username, String displayName, String password, PasswordEncoder encoder) {
        ApplicationUserEntity user = new ApplicationUserEntity();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setRole(UserRole.ADMIN);
        user.setPasswordHash(encoder.encode(password));
        user.setActive(true);
        user.setCreatedBy("DEV_SEED");
        user.setUpdatedBy("DEV_SEED");
        return user;
    }

    private ApplicationUserEntity driverUser(String username, DriverEntity driver, String password, PasswordEncoder encoder) {
        ApplicationUserEntity user = admin(username, driver.getName(), password, encoder);
        user.setRole(UserRole.DRIVER);
        user.setDriver(driver);
        return user;
    }

    private CustomerReferenceEntity customer(String externalId, String name) {
        CustomerReferenceEntity entity = new CustomerReferenceEntity();
        entity.setExternalId(externalId);
        entity.setName(name);
        entity.setActive(true);
        entity.setCreatedBy("DEV_SEED");
        entity.setUpdatedBy("DEV_SEED");
        return entity;
    }

    private ProductionTimeWindowEntity window(String label, LocalTime cutoff, int order) {
        ProductionTimeWindowEntity entity = new ProductionTimeWindowEntity();
        entity.setLabel(label);
        entity.setCutoffTime(cutoff);
        entity.setSortOrder(order);
        return entity;
    }

    private WheelIntakeRequestEntity request(DriverEntity driver, CustomerReferenceEntity customer, int quantity,
                                             LocalDate dropDate, String dropStart, String dropEnd,
                                             LocalDate pickupDate, String pickupStart, String pickupEnd,
                                             LifecycleStatus status, String notes, String actualArrival,
                                             Integer manualPriority, Integer actualReceived, boolean locked) {
        ZoneId zone = ZoneId.of("Europe/Lisbon");
        WheelIntakeRequestEntity entity = new WheelIntakeRequestEntity();
        entity.setSource(RequestSource.WEB);
        entity.setDriver(driver);
        entity.setCustomer(customer);
        entity.setCustomerExternalId(customer.getExternalId());
        entity.setCustomerNameSnapshot(customer.getName());
        entity.setExpectedWheelQuantity(quantity);
        entity.setActualReceivedWheelQuantity(actualReceived);
        entity.setQuantityDiscrepancyAcknowledged(false);
        entity.setExpectedFactoryDropOffWindowStart(at(dropDate, dropStart, zone));
        entity.setExpectedFactoryDropOffWindowEnd(at(dropDate, dropEnd, zone));
        entity.setRequestedFactoryPickupWindowStart(at(pickupDate, pickupStart, zone));
        entity.setRequestedFactoryPickupWindowEnd(at(pickupDate, pickupEnd, zone));
        entity.setLifecycleStatus(status);
        entity.setNotes(notes);
        entity.setManualPriority(manualPriority);
        entity.setPlanningLocked(locked);
        if (actualArrival != null) {
            entity.setActualFactoryArrivalAt(at(dropDate, actualArrival, zone));
        }
        entity.setCreatedBy("DEV_SEED");
        entity.setUpdatedBy("DEV_SEED");
        return entity;
    }

    private OffsetDateTime at(LocalDate date, String hhmm, ZoneId zone) {
        return date.atTime(LocalTime.parse(hhmm)).atZone(zone).toOffsetDateTime();
    }
}
