package pt.rucodel.productionplanning.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.entity.CustomerReferenceEntity;
import pt.rucodel.productionplanning.entity.DriverEntity;
import pt.rucodel.productionplanning.entity.ProductionSiteEntity;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class DevSeedDataConfigTest {

    @Test
    void seedCreatesDemoDriverWithDriverCodeForNonNumericExternalId() throws Exception {
        DriverRepository drivers = mock(DriverRepository.class);
        CustomerReferenceRepository customers = mock(CustomerReferenceRepository.class);
        WheelIntakeRequestRepository requests = mock(WheelIntakeRequestRepository.class);
        RequestStatusHistoryRepository history = mock(RequestStatusHistoryRepository.class);
        DailyProductionSettingsRepository settings = mock(DailyProductionSettingsRepository.class);
        ProductionSiteRepository productionSitesRepository = mock(ProductionSiteRepository.class);
        ProductionSiteService productionSites = mock(ProductionSiteService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneId.of("Europe/Lisbon"));
        ProductionSiteEntity portugal = site();
        ProductionSiteEntity luxembourg = site(ProductionSiteCode.LUX);

        when(requests.existsByRequestCode(any())).thenReturn(true);
        when(settings.findByProductionSite_CodeAndSettingsKey(any(), any())).thenReturn(Optional.empty());
        when(productionSitesRepository.findByCode(ProductionSiteCode.PT)).thenReturn(Optional.of(portugal));
        when(productionSitesRepository.findByCode(ProductionSiteCode.LUX)).thenReturn(Optional.of(luxembourg));
        when(drivers.findByNameIgnoreCase("teste")).thenReturn(Optional.empty());
        when(drivers.findByExternalId("DTESTE")).thenReturn(Optional.empty());
        when(drivers.findByDriverCode("MOTOR-DTESTE")).thenReturn(Optional.empty());
        when(drivers.save(any(DriverEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> null).when(productionSites).ensureDriverAssociation(any(), any(), any(), any());
        stubExistingSeedCustomers(customers, portugal, luxembourg);

        CommandLineRunner runner = new DevSeedDataConfig().seedDevelopmentData(
                drivers,
                customers,
                requests,
                history,
                settings,
                productionSitesRepository,
                productionSites,
                clock
        );

        runner.run();

        ArgumentCaptor<DriverEntity> captor = forClass(DriverEntity.class);
        verify(drivers).save(captor.capture());
        assertThat(captor.getValue().getExternalId()).isEqualTo("DTESTE");
        assertThat(captor.getValue().getDriverCode()).isEqualTo("MOTOR-DTESTE");
    }

    @Test
    void seedReusesExistingDriverCodeWhenAdminUserIsMissing() throws Exception {
        DriverRepository drivers = mock(DriverRepository.class);
        CustomerReferenceRepository customers = mock(CustomerReferenceRepository.class);
        WheelIntakeRequestRepository requests = mock(WheelIntakeRequestRepository.class);
        RequestStatusHistoryRepository history = mock(RequestStatusHistoryRepository.class);
        DailyProductionSettingsRepository settings = mock(DailyProductionSettingsRepository.class);
        ProductionSiteRepository productionSitesRepository = mock(ProductionSiteRepository.class);
        ProductionSiteService productionSites = mock(ProductionSiteService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneId.of("Europe/Lisbon"));
        ProductionSiteEntity portugal = site();

        when(requests.existsByRequestCode(any())).thenReturn(true);
        when(settings.findByProductionSite_CodeAndSettingsKey(any(), any())).thenReturn(Optional.empty());
        when(productionSitesRepository.findByCode(ProductionSiteCode.PT)).thenReturn(Optional.of(portugal));
        doAnswer(invocation -> null).when(productionSites).ensureDriverAssociation(any(), any(), any(), any());

        ProductionSiteEntity luxembourg = site(ProductionSiteCode.LUX);
        when(productionSitesRepository.findByCode(ProductionSiteCode.LUX)).thenReturn(Optional.of(luxembourg));
        DriverEntity existing = driver("DTESTE", "MOTOR-TESTE", "teste");
        when(drivers.findByNameIgnoreCase("teste")).thenReturn(Optional.of(existing));

        stubExistingSeedCustomers(customers, portugal, luxembourg);

        CommandLineRunner runner = new DevSeedDataConfig().seedDevelopmentData(
                drivers,
                customers,
                requests,
                history,
                settings,
                productionSitesRepository,
                productionSites,
                clock
        );

        runner.run();

        verify(drivers, never()).save(argThat(driver -> "teste".equals(driver.getName())));
    }

    private void stubExistingSeedCustomers(CustomerReferenceRepository customers, ProductionSiteEntity portugal,
                                           ProductionSiteEntity luxembourg) {
        when(customers.findByProductionSite_CodeAndExternalId(eq(ProductionSiteCode.PT), eq("C1001")))
                .thenReturn(Optional.of(customer(portugal, "C1001", "CLI-1001", "loja de jantes de AAAA")));
        when(customers.findByProductionSite_CodeAndExternalId(eq(ProductionSiteCode.PT), eq("C1002")))
                .thenReturn(Optional.of(customer(portugal, "C1002", "CLI-1002", "loja de jantes de BBBB")));
        when(customers.findByProductionSite_CodeAndExternalId(eq(ProductionSiteCode.PT), eq("C1003")))
                .thenReturn(Optional.of(customer(portugal, "C1003", "CLI-1003", "loja de jantes de CCCC")));
        when(customers.findByProductionSite_CodeAndExternalId(eq(ProductionSiteCode.PT), eq("C1004")))
                .thenReturn(Optional.of(customer(portugal, "C1004", "CLI-1004", "loja de jantes de DDDD")));
        when(customers.findByProductionSite_CodeAndExternalId(eq(ProductionSiteCode.LUX), eq("L1001")))
                .thenReturn(Optional.of(customer(luxembourg, "L1001", "CLI-1001", "loja de jantes de AAAA")));
        when(customers.findByProductionSite_CodeAndExternalId(eq(ProductionSiteCode.LUX), eq("L1002")))
                .thenReturn(Optional.of(customer(luxembourg, "L1002", "CLI-1002", "loja de jantes de BBBB")));
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
