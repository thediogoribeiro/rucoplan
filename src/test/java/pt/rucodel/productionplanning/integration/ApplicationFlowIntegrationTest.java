package pt.rucodel.productionplanning.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pt.rucodel.productionplanning.domain.UserRole;
import pt.rucodel.productionplanning.entity.*;
import pt.rucodel.productionplanning.repository.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationFlowIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DriverRepository drivers;
    @Autowired ApplicationUserRepository users;
    @Autowired CustomerReferenceRepository customers;
    @Autowired WheelIntakeRequestRepository requests;
    @Autowired RequestStatusHistoryRepository history;
    @Autowired ProductionPlanRepository plans;
    @Autowired ProductionPlanItemRepository planItems;
    @Autowired PlanningAuditEventRepository audit;
    @Autowired WhatsAppIngestionItemRepository ingestionItems;
    @Autowired DailyProductionSettingsRepository settings;

    private DriverEntity driverOne;
    private DriverEntity driverTwo;
    private CustomerReferenceEntity customerOne;
    private CustomerReferenceEntity customerTwo;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        planItems.deleteAll();
        plans.deleteAll();
        history.deleteAll();
        audit.deleteAll();
        ingestionItems.deleteAll();
        requests.deleteAll();
        users.deleteAll();
        customers.deleteAll();
        settings.deleteAll();
        drivers.deleteAll();

        date = LocalDate.of(2026, 9, 2);
        driverOne = drivers.save(driver("D001", "João Martins"));
        driverTwo = drivers.save(driver("D002", "Marta Silva"));
        customerOne = customers.save(customer("C1001", "Oficina Central Braga"));
        customerTwo = customers.save(customer("C1002", "Auto Reparadora Norte"));
        users.save(user("admin", "Administrador", UserRole.ADMIN, null));
        users.save(user("driver1", "João Martins", UserRole.DRIVER, driverOne));
        users.save(user("driver2", "Marta Silva", UserRole.DRIVER, driverTwo));
        settings.save(settings(date, 12, 8));
    }

    @Test
    void requestPersistenceRolePermissionsAndDriverIsolationWork() throws Exception {
        String driverToken = login("driver1");
        String otherDriverToken = login("driver2");
        String adminToken = login("admin");

        String createdJson = mockMvc.perform(post("/api/v1/driver/requests")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(customerOne.getId().toString(), "09:00", "10:00", "17:00", "18:00", 4)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerNameSnapshot").value("Oficina Central Braga"))
                .andExpect(jsonPath("$.wheelQuantities[2].type").value("NORMAL"))
                .andExpect(jsonPath("$.totalQuantity").value(4))
                .andReturn().getResponse().getContentAsString();
        String requestId = objectMapper.readTree(createdJson).get("id").asText();

        mockMvc.perform(get("/api/v1/driver/requests/{id}", requestId)
                        .header("Authorization", bearer(otherDriverToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/requests")
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/requests")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void requestApiAcceptsTypedWheelQuantitiesAndRejectsInvalidTypePayloads() throws Exception {
        String driverToken = login("driver1");

        String createdJson = mockMvc.perform(post("/api/v1/driver/requests")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typedRequestPayload(customerOne.getId().toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalQuantity").value(25))
                .andExpect(jsonPath("$.expectedWheelQuantity").value(25))
                .andExpect(jsonPath("$.wheelQuantities[0].type").value("BIPARTITE"))
                .andExpect(jsonPath("$.wheelQuantities[0].quantity").value(4))
                .andExpect(jsonPath("$.wheelQuantities[1].type").value("WASHED"))
                .andExpect(jsonPath("$.wheelQuantities[1].quantity").value(6))
                .andExpect(jsonPath("$.wheelQuantities[2].type").value("NORMAL"))
                .andExpect(jsonPath("$.wheelQuantities[2].quantity").value(15))
                .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(createdJson);
        WheelIntakeRequestEntity saved = requests.findById(java.util.UUID.fromString(created.get("id").asText())).orElseThrow();
        assertThat(saved.wheelQuantity(pt.rucodel.productionplanning.domain.WheelType.BIPARTITE)).isEqualTo(4);
        assertThat(saved.wheelQuantity(pt.rucodel.productionplanning.domain.WheelType.WASHED)).isEqualTo(6);
        assertThat(saved.wheelQuantity(pt.rucodel.productionplanning.domain.WheelType.NORMAL)).isEqualTo(15);

        mockMvc.perform(post("/api/v1/driver/requests")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "wheelQuantities": [
                                    {"type": "NORMAL", "quantity": 1},
                                    {"type": "NORMAL", "quantity": 2}
                                  ],
                                  "expectedFactoryDropOffWindowStart": "2026-09-02T09:00:00+01:00",
                                  "expectedFactoryDropOffWindowEnd": "2026-09-02T10:00:00+01:00",
                                  "requestedFactoryPickupWindowStart": "2026-09-02T17:00:00+01:00",
                                  "requestedFactoryPickupWindowEnd": "2026-09-02T18:00:00+01:00",
                                  "notes": "Teste"
                                }
                                """.formatted(customerOne.getId())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/driver/requests")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "wheelQuantities": [
                                    {"type": "UNKNOWN", "quantity": 1}
                                  ],
                                  "expectedFactoryDropOffWindowStart": "2026-09-02T09:00:00+01:00",
                                  "expectedFactoryDropOffWindowEnd": "2026-09-02T10:00:00+01:00",
                                  "requestedFactoryPickupWindowStart": "2026-09-02T17:00:00+01:00",
                                  "requestedFactoryPickupWindowEnd": "2026-09-02T18:00:00+01:00",
                                  "notes": "Teste"
                                }
                                """.formatted(customerOne.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void administratorCanConfirmArrivalQuantityAndProductionStatus() throws Exception {
        String driverToken = login("driver1");
        String adminToken = login("admin");
        JsonNode created = createDriverRequest(driverToken, customerOne, 5);
        long version = created.get("version").asLong();

        String arrivedJson = mockMvc.perform(post("/api/v1/admin/requests/{id}/arrival", created.get("id").asText())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualFactoryArrivalAt": "2026-09-02T09:15:00+01:00",
                                  "actualReceivedWheelQuantity": 4,
                                  "acknowledgeDiscrepancy": false,
                                  "reason": "Receção física",
                                  "version": %d
                                }
                                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("ARRIVED_AT_FACTORY"))
                .andExpect(jsonPath("$.quantityDiscrepancy").value(true))
                .andReturn().getResponse().getContentAsString();

        long arrivedVersion = objectMapper.readTree(arrivedJson).get("version").asLong();
        String quantityJson = mockMvc.perform(post("/api/v1/admin/requests/{id}/received-quantity", created.get("id").asText())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualReceivedWheelQuantity": 4,
                                  "acknowledgeDiscrepancy": true,
                                  "reason": "Diferença reconhecida",
                                  "version": %d
                                }
                                """.formatted(arrivedVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityDiscrepancyAcknowledged").value(true))
                .andReturn().getResponse().getContentAsString();

        long statusVersion = objectMapper.readTree(quantityJson).get("version").asLong();
        mockMvc.perform(post("/api/v1/admin/requests/{id}/status", created.get("id").asText())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "IN_PRODUCTION",
                                  "reason": "Entrou em produção",
                                  "version": %d
                                }
                                """.formatted(statusVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("IN_PRODUCTION"));
    }

    @Test
    void whatsappMessageIdempotencyAndAmbiguousCustomerReviewWork() throws Exception {
        String payload = whatsappPayload("wa-1", "D001", "C1001", "Oficina Central Braga");

        mockMvc.perform(post("/api/v1/integrations/whatsapp/requests")
                        .header("X-Integration-Token", "test-whatsapp-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"));

        mockMvc.perform(post("/api/v1/integrations/whatsapp/requests")
                        .header("X-Integration-Token", "test-whatsapp-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"));

        customers.save(customer(null, "Cliente Ambíguo"));
        customers.save(customer(null, "Cliente Ambíguo"));

        mockMvc.perform(post("/api/v1/integrations/whatsapp/requests")
                        .header("X-Integration-Token", "test-whatsapp-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(whatsappPayload("wa-2", "D001", null, "Cliente Ambíguo")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"));

        assertThat(requests.findAll()).hasSize(1);
        assertThat(ingestionItems.findByExternalMessageId("wa-2")).isPresent();
    }

    @Test
    void optimisticLockingRejectsStaleRequestUpdates() throws Exception {
        String driverToken = login("driver1");
        JsonNode created = createDriverRequest(driverToken, customerOne, 3);

        mockMvc.perform(patch("/api/v1/driver/requests/{id}", created.get("id").asText())
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "expectedWheelQuantity": 3,
                                  "expectedFactoryDropOffWindowStart": "2026-09-02T09:00:00+01:00",
                                  "expectedFactoryDropOffWindowEnd": "2026-09-02T10:00:00+01:00",
                                  "requestedFactoryPickupWindowStart": "2026-09-02T17:00:00+01:00",
                                  "requestedFactoryPickupWindowEnd": "2026-09-02T18:00:00+01:00",
                                  "notes": "Stale",
                                  "version": 999
                                }
                                """.formatted(customerOne.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("OPTIMISTIC_LOCK"));
    }

    @Test
    void planCreationAndRegenerationAfterRelevantChangeWork() throws Exception {
        String driverToken = login("driver1");
        String adminToken = login("admin");
        createDriverRequest(driverToken, customerOne, 4);

        mockMvc.perform(get("/api/v1/admin/plans?date=2026-09-02")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.totalKnownWheels").value(4));

        mockMvc.perform(put("/api/v1/admin/settings/daily?date=2026-09-02")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dailyCapacity": 4,
                                  "dailyTarget": 3,
                                  "fallbackMinutesPerWheel": 20,
                                  "timeWindows": [
                                    {"label":"Fim da manhã","cutoffTime":"12:30","sortOrder":1},
                                    {"label":"Fim do dia","cutoffTime":"18:30","sortOrder":2}
                                  ],
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/plans/generate?date=2026-09-02")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(2));
    }

    private JsonNode createDriverRequest(String token, CustomerReferenceEntity customer, int quantity) throws Exception {
        String response = mockMvc.perform(post("/api/v1/driver/requests")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(customer.getId().toString(), "09:00", "10:00", "17:00", "18:00", quantity)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private String requestPayload(String customerId, String dropStart, String dropEnd, String pickupStart, String pickupEnd, int quantity) {
        return """
                {
                  "customerId": "%s",
                  "expectedWheelQuantity": %d,
                  "expectedFactoryDropOffWindowStart": "2026-09-02T%s:00+01:00",
                  "expectedFactoryDropOffWindowEnd": "2026-09-02T%s:00+01:00",
                  "requestedFactoryPickupWindowStart": "2026-09-02T%s:00+01:00",
                  "requestedFactoryPickupWindowEnd": "2026-09-02T%s:00+01:00",
                  "notes": "Teste"
                }
                """.formatted(customerId, quantity, dropStart, dropEnd, pickupStart, pickupEnd);
    }

    private String typedRequestPayload(String customerId) {
        return """
                {
                  "customerId": "%s",
                  "wheelQuantities": [
                    {"type": "BIPARTITE", "quantity": 4},
                    {"type": "WASHED", "quantity": 6},
                    {"type": "NORMAL", "quantity": 15}
                  ],
                  "expectedFactoryDropOffWindowStart": "2026-09-02T09:00:00+01:00",
                  "expectedFactoryDropOffWindowEnd": "2026-09-02T10:00:00+01:00",
                  "requestedFactoryPickupWindowStart": "2026-09-02T17:00:00+01:00",
                  "requestedFactoryPickupWindowEnd": "2026-09-02T18:00:00+01:00",
                  "notes": "Teste"
                }
                """.formatted(customerId);
    }

    private String whatsappPayload(String messageId, String driverExternalId, String customerExternalId, String customerName) {
        String customerExternal = customerExternalId == null ? "null" : "\"" + customerExternalId + "\"";
        return """
                {
                  "externalMessageId": "%s",
                  "driverExternalId": "%s",
                  "customerExternalId": %s,
                  "customerName": "%s",
                  "wheelQuantity": 8,
                  "expectedFactoryDropOffWindowStart": "2026-09-02T13:00:00+01:00",
                  "expectedFactoryDropOffWindowEnd": "2026-09-02T14:00:00+01:00",
                  "requestedFactoryPickupWindowStart": "2026-09-04T13:00:00+01:00",
                  "requestedFactoryPickupWindowEnd": "2026-09-04T14:00:00+01:00",
                  "notes": "Teste WhatsApp"
                }
                """.formatted(messageId, driverExternalId, customerExternal, customerName);
    }

    private String login(String username) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"password"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private DriverEntity driver(String externalId, String name) {
        DriverEntity driver = new DriverEntity();
        driver.setExternalId(externalId);
        driver.setName(name);
        driver.setActive(true);
        driver.setCreatedBy("TEST");
        driver.setUpdatedBy("TEST");
        return driver;
    }

    private CustomerReferenceEntity customer(String externalId, String name) {
        CustomerReferenceEntity customer = new CustomerReferenceEntity();
        customer.setExternalId(externalId);
        customer.setName(name);
        customer.setActive(true);
        customer.setCreatedBy("TEST");
        customer.setUpdatedBy("TEST");
        return customer;
    }

    private ApplicationUserEntity user(String username, String displayName, UserRole role, DriverEntity driver) {
        ApplicationUserEntity user = new ApplicationUserEntity();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setDriver(driver);
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setActive(true);
        user.setCreatedBy("TEST");
        user.setUpdatedBy("TEST");
        return user;
    }

    private DailyProductionSettingsEntity settings(LocalDate date, int capacity, int target) {
        DailyProductionSettingsEntity settings = new DailyProductionSettingsEntity();
        settings.setSettingsKey("DATE:" + date);
        settings.setSettingsDate(date);
        settings.setDailyCapacity(capacity);
        settings.setDailyTarget(target);
        settings.setFallbackMinutesPerWheel(20);
        settings.setCreatedBy("TEST");
        settings.setUpdatedBy("TEST");
        settings.replaceTimeWindows(List.of(window("Fim da manhã", "12:30", 1), window("Fim do dia", "18:30", 2)));
        return settings;
    }

    private ProductionTimeWindowEntity window(String label, String cutoff, int order) {
        ProductionTimeWindowEntity window = new ProductionTimeWindowEntity();
        window.setLabel(label);
        window.setCutoffTime(LocalTime.parse(cutoff));
        window.setSortOrder(order);
        return window;
    }
}
