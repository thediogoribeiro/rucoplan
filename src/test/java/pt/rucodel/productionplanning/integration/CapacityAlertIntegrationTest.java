package pt.rucodel.productionplanning.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pt.rucodel.productionplanning.domain.*;
import pt.rucodel.productionplanning.dto.DailySettingsRequest;
import pt.rucodel.productionplanning.dto.RequestUpdateRequest;
import pt.rucodel.productionplanning.dto.TimeWindowDto;
import pt.rucodel.productionplanning.entity.*;
import pt.rucodel.productionplanning.repository.*;
import pt.rucodel.productionplanning.security.AuthenticatedUser;
import pt.rucodel.productionplanning.service.ProductionSettingsService;
import pt.rucodel.productionplanning.service.ProductionPlanService;
import pt.rucodel.productionplanning.service.WheelIntakeRequestService;

import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CapacityAlertIntegrationTest {
    @jakarta.annotation.Resource WheelIntakeRequestService intakeRequests;
    @jakarta.annotation.Resource ProductionPlanService productionPlans;
    @jakarta.annotation.Resource ProductionSettingsService productionSettings;
    @jakarta.annotation.Resource CapacityAlertRepository capacityAlerts;
    @jakarta.annotation.Resource DriverRepository drivers;
    @jakarta.annotation.Resource CustomerReferenceRepository customers;
    @jakarta.annotation.Resource WheelIntakeRequestRepository requests;
    @jakarta.annotation.Resource DailyProductionSettingsRepository settings;
    @jakarta.annotation.Resource ProductionPlanRepository plans;
    @jakarta.annotation.Resource ProductionPlanItemRepository planItems;
    @jakarta.annotation.Resource RequestStatusHistoryRepository history;
    @jakarta.annotation.Resource PlanningAuditEventRepository audit;
    @jakarta.annotation.Resource TelegramConversationRepository conversations;
    @jakarta.annotation.Resource TelegramIntakeDraftRepository drafts;
    @jakarta.annotation.Resource TelegramInboundUpdateRepository inboundUpdates;
    @jakarta.annotation.Resource WhatsAppIngestionItemRepository whatsapp;

    private DriverEntity driver;
    private CustomerReferenceEntity customer;
    private LocalDate date;
    private ZoneId zone;

    @BeforeEach
    void setUp() {
        inboundUpdates.deleteAll();
        conversations.deleteAll();
        drafts.deleteAll();
        capacityAlerts.deleteAll();
        planItems.deleteAll();
        plans.deleteAll();
        history.deleteAll();
        audit.deleteAll();
        whatsapp.deleteAll();
        requests.deleteAll();
        settings.deleteAll();
        customers.deleteAll();
        drivers.deleteAll();

        zone = ZoneId.of("Europe/Lisbon");
        date = LocalDate.of(2099, 9, 10);
        driver = drivers.save(driver("Motorista"));
        customer = customers.save(customer("Cliente"));
        settings.save(settings(date, 10));
    }

    @Test
    void loadAtOrBelowCapacityDoesNotGenerateAlertAndOverCapacityDoes() {
        createTelegramRequest("telegram-alert-1", 10);
        productionPlans.generate(date, GenerationTrigger.MANUAL, "TEST");

        assertThat(capacityAlerts.findAll()).isEmpty();

        createTelegramRequest("telegram-alert-2", 1);
        productionPlans.generate(date, GenerationTrigger.MANUAL, "TEST");

        List<CapacityAlertEntity> alerts = capacityAlerts.findAll();
        assertThat(alerts).hasSize(1);
        CapacityAlertEntity alert = alerts.getFirst();
        assertThat(alert.getType()).isEqualTo(CapacityAlertType.OVERTIME_REQUIRED);
        assertThat(alert.getStatus()).isEqualTo(CapacityAlertStatus.ACTIVE);
        assertThat(alert.getRequiredQuantity()).isEqualTo(11);
        assertThat(alert.getAvailableCapacity()).isEqualTo(10);
        assertThat(alert.getDeficit()).isEqualTo(1);
    }

    @Test
    void telegramRequestIsAvailableInDailyProductionPlan() {
        WheelIntakeRequestEntity request = createTelegramRequest("telegram-plan-1", 4);

        pt.rucodel.productionplanning.dto.PlanResponse plan = productionPlans.generate(
                date,
                GenerationTrigger.MANUAL,
                "TEST"
        );

        assertThat(plan.totalKnownWheels()).isEqualTo(4);
        assertThat(plan.items()).anySatisfy(item -> {
            assertThat(item.requestId()).isEqualTo(request.getId());
            assertThat(item.quantity()).isEqualTo(4);
        });
    }

    @Test
    void capacityIncreaseResolvesActiveAlertAndCancellationCanResolveIt() {
        WheelIntakeRequestEntity request = createTelegramRequest("telegram-alert-3", 12);
        productionPlans.generate(date, GenerationTrigger.MANUAL, "TEST");
        CapacityAlertEntity firstAlert = capacityAlerts.findAll().getFirst();
        assertThat(firstAlert.getStatus()).isEqualTo(CapacityAlertStatus.ACTIVE);

        productionSettings.update(date, new DailySettingsRequest(12, 12, 1,
                List.of(new TimeWindowDto("Fim do dia", LocalTime.of(18, 30), 1)),
                settings.findBySettingsDate(date).orElseThrow().getVersion()), "TEST");

        assertThat(capacityAlerts.findAll().getFirst().getStatus()).isEqualTo(CapacityAlertStatus.RESOLVED);

        productionSettings.update(date, new DailySettingsRequest(10, 10, 1,
                List.of(new TimeWindowDto("Fim do dia", LocalTime.of(18, 30), 1)),
                settings.findBySettingsDate(date).orElseThrow().getVersion()), "TEST");
        assertThat(capacityAlerts.findByStatusInOrderByAffectedDateAscCreatedAtAsc(List.of(CapacityAlertStatus.ACTIVE))).hasSize(1);

        long currentVersion = requests.findById(request.getId()).orElseThrow().getVersion();
        intakeRequests.cancelForDriver(request.getId(), driver.getId(),
                new RequestUpdateRequest(null, null, null, null, null, null, null,
                        "Teste", null, null, currentVersion),
                new AuthenticatedUser(UUID.randomUUID(), "driver", "Motorista", UserRole.DRIVER, driver.getId()));
        productionPlans.generate(date, GenerationTrigger.MANUAL, "TEST");

        assertThat(capacityAlerts.findByStatusInOrderByAffectedDateAscCreatedAtAsc(List.of(CapacityAlertStatus.ACTIVE))).isEmpty();
    }

    private WheelIntakeRequestEntity createTelegramRequest(String externalMessageId, int quantity) {
        return intakeRequests.createFromTelegram(
                externalMessageId,
                driver,
                customer,
                quantity,
                date.atTime(9, 0).atZone(zone).toOffsetDateTime(),
                date.atTime(14, 0).atZone(zone).toOffsetDateTime(),
                FactoryTimeSlot.MORNING_09_14,
                date.atTime(14, 0).atZone(zone).toOffsetDateTime(),
                date.atTime(19, 0).atZone(zone).toOffsetDateTime(),
                FactoryTimeSlot.AFTERNOON_14_19,
                null
        );
    }

    private DriverEntity driver(String name) {
        DriverEntity entity = new DriverEntity();
        entity.setName(name);
        entity.setActive(true);
        entity.setCreatedBy("TEST");
        entity.setUpdatedBy("TEST");
        return entity;
    }

    private CustomerReferenceEntity customer(String name) {
        CustomerReferenceEntity entity = new CustomerReferenceEntity();
        entity.setName(name);
        entity.setActive(true);
        entity.setCreatedBy("TEST");
        entity.setUpdatedBy("TEST");
        return entity;
    }

    private DailyProductionSettingsEntity settings(LocalDate date, int capacity) {
        DailyProductionSettingsEntity entity = new DailyProductionSettingsEntity();
        entity.setSettingsKey("DATE:" + date);
        entity.setSettingsDate(date);
        entity.setDailyCapacity(capacity);
        entity.setDailyTarget(capacity);
        entity.setFallbackMinutesPerWheel(1);
        entity.setCreatedBy("TEST");
        entity.setUpdatedBy("TEST");
        ProductionTimeWindowEntity window = new ProductionTimeWindowEntity();
        window.setLabel("Fim do dia");
        window.setCutoffTime(LocalTime.of(18, 30));
        window.setSortOrder(1);
        entity.replaceTimeWindows(List.of(window));
        return entity;
    }
}
