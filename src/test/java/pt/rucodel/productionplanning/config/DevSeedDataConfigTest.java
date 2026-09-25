package pt.rucodel.productionplanning.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import pt.rucodel.productionplanning.entity.CustomerReferenceEntity;
import pt.rucodel.productionplanning.entity.DriverEntity;
import pt.rucodel.productionplanning.repository.ApplicationUserRepository;
import pt.rucodel.productionplanning.repository.CustomerReferenceRepository;
import pt.rucodel.productionplanning.repository.DailyProductionSettingsRepository;
import pt.rucodel.productionplanning.repository.DriverRepository;
import pt.rucodel.productionplanning.repository.RequestStatusHistoryRepository;
import pt.rucodel.productionplanning.repository.WheelIntakeRequestRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevSeedDataConfigTest {

    @Test
    void seedReusesExistingDriverCodeWhenAdminUserIsMissing() throws Exception {
        DriverRepository drivers = mock(DriverRepository.class);
        ApplicationUserRepository users = mock(ApplicationUserRepository.class);
        CustomerReferenceRepository customers = mock(CustomerReferenceRepository.class);
        WheelIntakeRequestRepository requests = mock(WheelIntakeRequestRepository.class);
        RequestStatusHistoryRepository history = mock(RequestStatusHistoryRepository.class);
        DailyProductionSettingsRepository settings = mock(DailyProductionSettingsRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneId.of("Europe/Lisbon"));

        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(users.existsByUsername(any())).thenReturn(false);
        when(requests.existsByRequestCode(any())).thenReturn(true);
        when(settings.findBySettingsKey(any())).thenReturn(Optional.empty());

        DriverEntity existingD1 = driver("D001", "MOTOR-001", "João Martins");
        DriverEntity existingD2 = driver("D002", "MOTOR-002", "Marta Silva");
        DriverEntity existingD3 = driver("D003", "MOTOR-003", "Rui Costa");
        when(drivers.findByExternalId("D001")).thenReturn(Optional.of(existingD1));
        when(drivers.findByExternalId("D002")).thenReturn(Optional.of(existingD2));
        when(drivers.findByExternalId("D003")).thenReturn(Optional.of(existingD3));

        when(customers.findByExternalId("C1001")).thenReturn(Optional.of(customer("C1001", "CLI-1001", "Oficina Central Braga")));
        when(customers.findByExternalId("C1002")).thenReturn(Optional.of(customer("C1002", "CLI-1002", "Auto Reparadora Norte")));
        when(customers.findByExternalId("C1003")).thenReturn(Optional.of(customer("C1003", "CLI-1003", "Pneus Atlântico")));
        when(customers.findByExternalId("C1004")).thenReturn(Optional.of(customer("C1004", "CLI-1004", "Jantes e Companhia")));

        CommandLineRunner runner = new DevSeedDataConfig().seedDevelopmentData(
                drivers,
                users,
                customers,
                requests,
                history,
                settings,
                passwordEncoder,
                clock
        );

        runner.run();

        verify(drivers, never()).save(argThat(driver -> "MOTOR-001".equals(driver.getDriverCode())));
        verify(users).save(argThat(user -> "admin".equals(user.getUsername())));
    }

    private DriverEntity driver(String externalId, String driverCode, String name) {
        DriverEntity entity = new DriverEntity();
        entity.setExternalId(externalId);
        entity.setDriverCode(driverCode);
        entity.setName(name);
        entity.setActive(true);
        return entity;
    }

    private CustomerReferenceEntity customer(String externalId, String customerCode, String name) {
        CustomerReferenceEntity entity = new CustomerReferenceEntity();
        entity.setExternalId(externalId);
        entity.setCustomerCode(customerCode);
        entity.setName(name);
        entity.setActive(true);
        return entity;
    }
}
