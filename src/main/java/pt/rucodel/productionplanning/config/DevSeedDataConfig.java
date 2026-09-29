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
import pt.rucodel.productionplanning.service.ProductionSiteService;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

@Configuration
@Profile("local")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DevSeedDataConfig {
    @Bean
    CommandLineRunner seedDevelopmentData(DriverRepository drivers,
                                          ApplicationUserRepository users,
                                          CustomerReferenceRepository customers,
                                          WheelIntakeRequestRepository requests,
                                          RequestStatusHistoryRepository history,
                                          DailyProductionSettingsRepository settings,
                                          ProductionSiteRepository productionSites,
                                          ProductionSiteService productionSiteService,
                                          PasswordEncoder passwordEncoder,
                                          Clock clock) {
        return args -> {
            ZoneId zone = ZoneId.of("Europe/Lisbon");
            LocalDate today = LocalDate.now(clock.withZone(zone));
            ProductionSiteEntity portugal = productionSites.findByCode(pt.rucodel.productionplanning.domain.ProductionSiteCode.PT)
                    .orElseThrow();
            ProductionSiteEntity luxembourg = productionSites.findByCode(pt.rucodel.productionplanning.domain.ProductionSiteCode.LUX)
                    .orElseThrow();

            DriverEntity demoDriver = findOrCreateDriver(drivers, "DTESTE", "teste");
            productionSiteService.ensureDriverAssociation(demoDriver, portugal,
                    pt.rucodel.productionplanning.domain.DriverProductionSiteAssociationSource.MIGRATION, "DEV_SEED");
            productionSiteService.ensureDriverAssociation(demoDriver, luxembourg,
                    pt.rucodel.productionplanning.domain.DriverProductionSiteAssociationSource.MIGRATION, "DEV_SEED");

            productionSiteService.ensureUserAssociation(
                    createUserIfMissing(users, admin("admin", "Administrador", "admin123", passwordEncoder)),
                    portugal, "DEV_SEED");
            productionSiteService.ensureUserAssociation(
                    createUserIfMissing(users, admin("admin", "Administrador", "admin123", passwordEncoder)),
                    luxembourg, "DEV_SEED");
            productionSiteService.ensureUserAssociation(
                    createUserIfMissing(users, driverUser("driver-teste", demoDriver, "driver123", passwordEncoder)),
                    portugal, "DEV_SEED");
            productionSiteService.ensureUserAssociation(
                    createUserIfMissing(users, driverUser("driver-teste", demoDriver, "driver123", passwordEncoder)),
                    luxembourg, "DEV_SEED");

            CustomerReferenceEntity c1 = findOrCreateCustomer(customers, portugal, "C1001", "loja de jantes de AAAA");
            CustomerReferenceEntity c2 = findOrCreateCustomer(customers, portugal, "C1002", "loja de jantes de BBBB");
            CustomerReferenceEntity c3 = findOrCreateCustomer(customers, portugal, "C1003", "loja de jantes de CCCC");
            CustomerReferenceEntity c4 = findOrCreateCustomer(customers, portugal, "C1004", "loja de jantes de DDDD");
            CustomerReferenceEntity l1 = findOrCreateCustomer(customers, luxembourg, "L1001", "loja de jantes de AAAA");
            CustomerReferenceEntity l2 = findOrCreateCustomer(customers, luxembourg, "L1002", "loja de jantes de BBBB");

            createSettingsIfMissing(settings, portugal, today);
            createSettingsIfMissing(settings, luxembourg, today);

            List<WheelIntakeRequestEntity> seededRequests = List.of(
                    request("REQ-DEV000001", demoDriver, c1, 4, today, "08:00", "09:00", today, "16:30", "17:30",
                            LifecycleStatus.AT_FACTORY, "Pedido já na fábrica.", "07:50", null, null, false),
                    request("REQ-DEV000002", demoDriver, c2, 6, today, "13:00", "14:00", today, "17:30", "18:30",
                            LifecycleStatus.COMMUNICATED, "Chegada prevista à hora de almoço.", null, null, null, false),
                    request("REQ-DEV000003", demoDriver, c3, 5, today, "20:00", "21:00", today.plusDays(1), "09:00", "10:00",
                            LifecycleStatus.COMMUNICATED, "Chegada no fim do dia; normalmente passa para amanhã.", null, null, null, false),
                    request("REQ-DEV000004", demoDriver, c4, 7, today, "07:30", "08:00", today, "10:30", "11:00",
                            LifecycleStatus.AT_FACTORY, "Prazo apertado para demonstrar risco.", "07:35", null, null, false),
                    request("REQ-DEV000005", demoDriver, c1, 18, today, "09:00", "10:00", today, "18:00", "19:00",
                            LifecycleStatus.COMMUNICATED, "Pedido grande para demonstrar excesso de capacidade.", null, 1, null, false),
                    request("REQ-DEV000006", demoDriver, c2, 3, today.minusDays(1), "15:00", "16:00", today, "15:00", "16:00",
                            LifecycleStatus.IN_PRODUCTION, "Quantidade recebida diferente da prevista.", "16:10", null, 2, false),
                    request("REQ-LUX000001", demoDriver, l1, 3, today, "09:00", "10:00", today, "15:00", "16:00",
                            LifecycleStatus.COMMUNICATED, "Pedido local de Luxemburgo.", null, null, null, false),
                    request("REQ-LUX000002", demoDriver, l2, 4, today.plusDays(1), "09:00", "10:00", today.plusDays(1), "16:00", "17:00",
                            LifecycleStatus.COMMUNICATED, "Pedido local de Luxemburgo.", null, null, null, false)
            );
            List<WheelIntakeRequestEntity> newRequests = new ArrayList<>();
            for (WheelIntakeRequestEntity seeded : seededRequests) {
                if (!requests.existsByRequestCode(seeded.getRequestCode())) {
                    newRequests.add(seeded);
                }
            }
            requests.saveAll(newRequests);
            for (WheelIntakeRequestEntity seeded : newRequests) {
                history.save(RequestStatusHistoryEntity.create(seeded, null, seeded.getLifecycleStatus(),
                        OffsetDateTime.now(clock), null, "DEV_SEED", "Seed development status."));
            }
        };
    }

    private DriverEntity findOrCreateDriver(DriverRepository drivers, String externalId, String name) {
        String driverCode = codeFromExternalId("MOTOR-", externalId);
        return drivers.findByNameIgnoreCase(name)
                .or(() -> drivers.findByExternalId(externalId))
                .or(() -> driverCode == null ? java.util.Optional.empty() : drivers.findByDriverCode(driverCode))
                .orElseGet(() -> drivers.save(driver(externalId, name)));
    }

    private CustomerReferenceEntity findOrCreateCustomer(CustomerReferenceRepository customers, ProductionSiteEntity site,
                                                        String externalId, String name) {
        String customerCode = codeFromExternalId("CLI-", externalId);
        Integer customerNumber = numericSuffix(externalId);
        CustomerReferenceEntity customer = customers.findByProductionSite_CodeAndExternalId(site.getCode(), externalId)
                .or(() -> customerNumber == null
                        ? java.util.Optional.empty()
                        : customers.findByProductionSite_CodeAndCustomerNumber(site.getCode(), customerNumber))
                .or(() -> customerCode == null
                        ? java.util.Optional.empty()
                        : customers.findByProductionSite_CodeAndCustomerCode(site.getCode(), customerCode))
                .orElseGet(() -> customers.save(customer(site, externalId, name)));
        if (!name.equals(customer.getName())) {
            customer.setName(name);
            customer.setNormalizedName(null);
            customer.setUpdatedBy("DEV_SEED");
            customer = customers.save(customer);
        }
        return customer;
    }

    private ApplicationUserEntity createUserIfMissing(ApplicationUserRepository users, ApplicationUserEntity user) {
        return users.findByUsername(user.getUsername()).orElseGet(() -> users.save(user));
    }

    private void createSettingsIfMissing(DailyProductionSettingsRepository settings, ProductionSiteEntity site, LocalDate today) {
        String key = "DATE:" + today;
        if (settings.findByProductionSite_CodeAndSettingsKey(site.getCode(), key).isPresent()) {
            return;
        }
        DailyProductionSettingsEntity daily = new DailyProductionSettingsEntity();
        daily.setProductionSite(site);
        daily.setSettingsKey(key);
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
    }

    private String codeFromExternalId(String prefix, String externalId) {
        Integer suffix = numericSuffix(externalId);
        if (suffix == null) {
            return null;
        }
        return prefix + String.format("%03d", suffix);
    }

    private Integer numericSuffix(String externalId) {
        if (externalId == null || externalId.length() < 2 || !externalId.substring(1).matches("[0-9]+")) {
            return null;
        }
        return Integer.parseInt(externalId.substring(1));
    }

    private DriverEntity driver(String externalId, String name) {
        DriverEntity entity = new DriverEntity();
        entity.setExternalId(externalId);
        if (externalId != null && externalId.matches("D[0-9]+")) {
            entity.setDriverCode("MOTOR-" + String.format("%03d", Integer.parseInt(externalId.substring(1))));
        }
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

    private CustomerReferenceEntity customer(ProductionSiteEntity site, String externalId, String name) {
        CustomerReferenceEntity entity = new CustomerReferenceEntity();
        entity.setProductionSite(site);
        entity.setExternalId(externalId);
        Integer customerNumber = numericSuffix(externalId);
        if (customerNumber != null) {
            entity.setCustomerNumber(customerNumber);
            entity.setCustomerCode("CLI-" + String.format("%03d", customerNumber));
        }
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

    private WheelIntakeRequestEntity request(String requestCode, DriverEntity driver, CustomerReferenceEntity customer, int quantity,
                                             LocalDate dropDate, String dropStart, String dropEnd,
                                             LocalDate pickupDate, String pickupStart, String pickupEnd,
                                             LifecycleStatus status, String notes, String actualArrival,
                                             Integer manualPriority, Integer actualReceived, boolean locked) {
        ZoneId zone = ZoneId.of("Europe/Lisbon");
        WheelIntakeRequestEntity entity = new WheelIntakeRequestEntity();
        entity.setRequestCode(requestCode);
        entity.setSource(RequestSource.WEB);
        entity.setProductionSite(customer.getProductionSite());
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
            entity.setArrivalConfirmedAt(at(dropDate, actualArrival, zone));
            entity.setArrivalConfirmedBy("DEV_SEED");
            entity.setArrivalConfirmationSource("ADMIN");
        }
        entity.setCreatedBy("DEV_SEED");
        entity.setUpdatedBy("DEV_SEED");
        return entity;
    }

    private OffsetDateTime at(LocalDate date, String hhmm, ZoneId zone) {
        return date.atTime(LocalTime.parse(hhmm)).atZone(zone).toOffsetDateTime();
    }
}
