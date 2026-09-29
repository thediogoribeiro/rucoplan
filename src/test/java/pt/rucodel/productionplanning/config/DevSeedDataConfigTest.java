package pt.rucodel.productionplanning.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.entity.ApplicationUserEntity;
import pt.rucodel.productionplanning.entity.CustomerReferenceEntity;
import pt.rucodel.productionplanning.entity.DriverEntity;
import pt.rucodel.productionplanning.entity.ProductionSiteEntity;
import pt.rucodel.productionplanning.repository.ApplicationUserRepository;
import pt.rucodel.productionplanning.repository.CustomerReferenceRepository;
import pt.rucodel.productionplanning.repository.DailyProductionSettingsRepository;
import pt.rucodel.productionplanning.repository.DriverRepository;
import pt.rucodel.productionplanning.repository.ProductionSiteRepository;
import pt.rucodel.productionplanning.repository.RequestStatusHistoryRepository;
import pt.rucodel.productionplanning.repository.WheelIntakeRequestRepository;
import pt.rucodel.productionplanning.service.ProductionSiteService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class DevSeedDataConfigTest {

    @Test
    void seedReusesExistingDriverCodeWhenAdminUserIsMissing() throws Exception {
        DriverRepository drivers = mock(DriverRepository.class);
        ApplicationUserRepository users = mock(ApplicationUserRepository.class);
        CustomerReferenceRepository customers = mock(CustomerReferenceRepository.class);
        WheelIntakeRequestRepository requests = mock(WheelIntakeRequestRepository.class);
        RequestStatusHistoryRepository history = mock(RequestStatusHistoryRepository.class);
        DailyProductionSettingsRepository settings = mock(DailyProductionSettingsRepository.class);
        ProductionSiteRepository productionSitesRepository = mock(ProductionSiteRepository.class);
        ProductionSiteService productionSites = mock(ProductionSiteService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneId.of("Europe/Lisbon"));
        ProductionSiteEntity portugal = site();

        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(users.findByUsername(any())).thenReturn(Optional.empty());
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0, ApplicationUserEntity.class));
        when(requests.existsByRequestCode(any())).thenReturn(true);
        when(settings.findByProductionSite_CodeAndSettingsKey(any(), any())).thenReturn(Optional.empty());
        when(productionSitesRepository.findByCode(ProductionSiteCode.PT)).thenReturn(Optional.of(portugal));
        doAnswer(invocation -> null).when(productionSites).ensureDriverAssociation(any(), any(), any(), any());
        doAnswer(invocation -> null).when(productionSites).ensureUserAssociation(any(), any(), any());

        ProductionSiteEntity luxembourg = site(ProductionSiteCode.LUX);
        when(productionSitesRepository.findByCode(ProductionSiteCode.LUX)).thenReturn(Optional.of(luxembourg));
        DriverEntity existing = driver("DTESTE", "MOTOR-TESTE", "teste");
        when(drivers.findByNameIgnoreCase("teste")).thenReturn(Optional.of(existing));

        when(customers.findByProductionSite_CodeAndExternalId(ProductionSiteCode.PT, "C1001"))
                .thenReturn(Optional.of(customer(portugal, "C1001", "CLI-1001", "loja de jantes de AAAA")));
        when(customers.findByProductionSite_CodeAndExternalId(ProductionSiteCode.PT, "C1002"))
                .thenReturn(Optional.of(customer(portugal, "C1002", "CLI-1002", "loja de jantes de BBBB")));
        when(customers.findByProductionSite_CodeAndExternalId(ProductionSiteCode.PT, "C1003"))
                .thenReturn(Optional.of(customer(portugal, "C1003", "CLI-1003", "loja de jantes de CCCC")));
        when(customers.findByProductionSite_CodeAndExternalId(ProductionSiteCode.PT, "C1004"))
                .thenReturn(Optional.of(customer(portugal, "C1004", "CLI-1004", "loja de jantes de DDDD")));
        when(customers.findByProductionSite_CodeAndExternalId(ProductionSiteCode.LUX, "L1001"))
                .thenReturn(Optional.of(customer(luxembourg, "L1001", "CLI-1001", "loja de jantes de AAAA")));
        when(customers.findByProductionSite_CodeAndExternalId(ProductionSiteCode.LUX, "L1002"))
                .thenReturn(Optional.of(customer(luxembourg, "L1002", "CLI-1002", "loja de jantes de BBBB")));

        CommandLineRunner runner = new DevSeedDataConfig().seedDevelopmentData(
                drivers,
                users,
                customers,
                requests,
                history,
                settings,
                productionSitesRepository,
                productionSites,
                passwordEncoder,
                clock
        );

        runner.run();

        verify(drivers, never()).save(argThat(driver -> "teste".equals(driver.getName())));
        verify(users, atLeastOnce()).save(argThat(user -> "admin".equals(user.getUsername())));
    }

    private DriverEntity driver(String externalId, String driverCode, String name) {
        DriverEntity entity = new DriverEntity();
        entity.setExternalId(externalId);
        entity.setDriverCode(driverCode);
        entity.setName(name);
        entity.setActive(true);
        return entity;
    }

    private ProductionSiteEntity site() {
        return site(ProductionSiteCode.PT);
    }

    private ProductionSiteEntity site(ProductionSiteCode code) {
        ProductionSiteEntity entity = new ProductionSiteEntity();
        entity.setCode(code);
        entity.setDisplayName(code.displayName());
        entity.setTimezone(code.timezone());
        entity.setActive(true);
        return entity;
    }

    private CustomerReferenceEntity customer(ProductionSiteEntity site, String externalId, String customerCode, String name) {
        CustomerReferenceEntity entity = new CustomerReferenceEntity();
        entity.setProductionSite(site);
        entity.setExternalId(externalId);
        entity.setCustomerCode(customerCode);
        entity.setName(name);
        entity.setActive(true);
        return entity;
    }
}
