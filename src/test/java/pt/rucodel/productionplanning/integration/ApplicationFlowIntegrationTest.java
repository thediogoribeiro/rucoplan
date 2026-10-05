package pt.rucodel.productionplanning.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pt.rucodel.productionplanning.domain.UserRole;
import pt.rucodel.productionplanning.domain.DriverProductionSiteAssociationSource;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.domain.WheelType;
import pt.rucodel.productionplanning.entity.*;
import pt.rucodel.productionplanning.repository.*;
import pt.rucodel.productionplanning.service.ProductionSiteService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    @Autowired ApplicationUserSiteRepository userSites;
    @Autowired DriverProductionSiteRepository driverSites;
    @Autowired ProductionSiteService productionSites;
    @Autowired JdbcTemplate jdbcTemplate;

    private DriverEntity driverOne;
    private DriverEntity driverTwo;
    private CustomerReferenceEntity customerOne;
    private CustomerReferenceEntity customerTwo;
    private CustomerReferenceEntity luxCustomer;
    private ProductionSiteEntity portugal;
    private ProductionSiteEntity luxembourg;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS customer_number_seq START WITH 1000 INCREMENT BY 1");
        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS customer_code_seq START WITH 1 INCREMENT BY 1");
        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS driver_code_seq START WITH 1 INCREMENT BY 1");
        planItems.deleteAll();
        plans.deleteAll();
        history.deleteAll();
        audit.deleteAll();
        ingestionItems.deleteAll();
        requests.deleteAll();
        userSites.deleteAll();
        users.deleteAll();
        customers.deleteAll();
        settings.deleteAll();
        driverSites.deleteAll();
        drivers.deleteAll();

        date = LocalDate.of(2026, 9, 2);
        portugal = productionSites.requireByCode(ProductionSiteCode.PT);
        luxembourg = productionSites.requireByCode(ProductionSiteCode.LUX);
        driverOne = drivers.save(driver("D001", "João Martins"));
        driverTwo = drivers.save(driver("D002", "Marta Silva"));
        productionSites.ensureDriverAssociation(driverOne, portugal, DriverProductionSiteAssociationSource.ADMIN, "TEST");
        productionSites.ensureDriverAssociation(driverTwo, portugal, DriverProductionSiteAssociationSource.ADMIN, "TEST");
        productionSites.ensureDriverAssociation(driverOne, luxembourg, DriverProductionSiteAssociationSource.ADMIN, "TEST");
        customerOne = customers.save(customer("C1001", "Oficina Central Braga"));
        customerTwo = customers.save(customer("C1002", "Auto Reparadora Norte"));
        luxCustomer = customers.save(customer(luxembourg, "L1001", "Lux Wheels"));
        ApplicationUserEntity admin = users.save(user("admin", "Administrador", UserRole.ADMIN, null));
        ApplicationUserEntity luxAdmin = users.save(user("luxadmin", "Administrador LUX", UserRole.ADMIN, null));
        ApplicationUserEntity userOne = users.save(user("driver1", "João Martins", UserRole.DRIVER, driverOne));
        ApplicationUserEntity userTwo = users.save(user("driver2", "Marta Silva", UserRole.DRIVER, driverTwo));
        productionSites.ensureUserAssociation(admin, portugal, "TEST");
        productionSites.ensureUserAssociation(luxAdmin, luxembourg, "TEST");
        productionSites.ensureUserAssociation(userOne, portugal, "TEST");
        productionSites.ensureUserAssociation(userOne, luxembourg, "TEST");
        productionSites.ensureUserAssociation(userTwo, portugal, "TEST");
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
    void luxRejectsBipartiteQuantitiesAndPortugalStillAcceptsThem() throws Exception {
        String ptDriverToken = login("driver1", "PT");
        String luxDriverToken = login("driver1", "LUX");

        mockMvc.perform(post("/api/v1/driver/requests")
                        .header("Authorization", bearer(ptDriverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typedRequestPayload(customerOne.getId().toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.wheelQuantities[0].quantity").value(4));

        mockMvc.perform(post("/api/v1/driver/requests")
                        .header("Authorization", bearer(luxDriverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typedRequestPayload(luxCustomer.getId().toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("LUX_BIPARTITE_NOT_ALLOWED"));
    }

    @Test
    void systemDiagnosticsRequireAdminAndAreSanitized() throws Exception {
        String driverToken = login("driver1");
        String adminToken = login("admin");

        mockMvc.perform(get("/api/v1/admin/system-diagnostics"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/problem+json")))
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        mockMvc.perform(get("/api/v1/admin/system-diagnostics")
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/problem+json")))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        String response = mockMvc.perform(get("/api/v1/admin/system-diagnostics")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backend.status").value("UP"))
                .andExpect(jsonPath("$.database.status").value("UP"))
                .andExpect(jsonPath("$.database.schemaStatus").value("UP"))
                .andExpect(jsonPath("$.planning.confirmedRequestsErrorCode").doesNotExist())
                .andExpect(jsonPath("$.planning.openPlansErrorCode").doesNotExist())
                .andExpect(jsonPath("$.planning.planLinesErrorCode").doesNotExist())
                .andExpect(jsonPath("$.planning.targetsUsed.minimumDailyTarget").value(150))
                .andExpect(jsonPath("$.planning.targetsUsed.regularDailyCapacity").value(180))
                .andReturn().getResponse().getContentAsString();

        assertThat(response.toLowerCase()).doesNotContain("jdbc", "password", "username");

        mockMvc.perform(post("/api/v1/auth/stream-session")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("RUCOPLAN_STREAM_AUTH=")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("access_token"))));
    }

    @Test
    void recalculationUsesDatedEndpointAndStaticAssetsAreRevalidated() throws Exception {
        String adminToken = login("admin");

        mockMvc.perform(post("/api/v1/admin/production-plans/{date}/recalculate", date)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planningDate").value(date.toString()));

        mockMvc.perform(post("/api/v1/admin/production-plans/recalculate")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(get("/admin.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=0")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("must-revalidate")));
    }

    @Test
    void customerCountryNameIsEditablePersistedAndSiteScoped() throws Exception {
        String adminToken = login("admin", "PT");
        String luxAdminToken = login("luxadmin", "LUX");

        String createdPt = mockMvc.perform(post("/api/v1/admin/customers")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Cliente França",
                                  "taxIdentifier": "FR123",
                                  "countryName": "França",
                                  "locality": "Paris",
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.countryName").value("França"))
                .andReturn().getResponse().getContentAsString();

        String createdLux = mockMvc.perform(post("/api/v1/admin/customers")
                        .header("Authorization", bearer(luxAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Cliente Luxemburgo",
                                  "taxIdentifier": "LU123",
                                  "countryName": "Luxemburgo",
                                  "locality": "Luxemburgo",
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.countryName").value("Luxemburgo"))
                .andReturn().getResponse().getContentAsString();

        JsonNode pt = objectMapper.readTree(createdPt);
        JsonNode lux = objectMapper.readTree(createdLux);
        assertThat(customers.findById(java.util.UUID.fromString(pt.get("id").asText())).orElseThrow().getCountryName())
                .isEqualTo("França");
        assertThat(customers.findByIdForSite(ProductionSiteCode.LUX, java.util.UUID.fromString(lux.get("id").asText())))
                .isPresent();
        assertThat(customers.findByIdForSite(ProductionSiteCode.PT, java.util.UUID.fromString(lux.get("id").asText())))
                .isEmpty();

        mockMvc.perform(get("/api/v1/admin/customers")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Cliente França')].countryName").value(org.hamcrest.Matchers.contains("França")))
                .andExpect(jsonPath("$[?(@.name == 'Cliente Luxemburgo')]").isEmpty());

        mockMvc.perform(post("/api/v1/admin/customers")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Cliente Sem País",
                                  "countryName": "",
                                  "active": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CUSTOMER_COUNTRY_REQUIRED"));
    }

    @Test
    void administratorCanConfirmArrivalQuantityAndProductionStatus() throws Exception {
        String driverToken = login("driver1");
        String adminToken = login("admin");
        String otherDriverToken = login("driver2");
        JsonNode created = createDriverRequest(driverToken, customerOne, 5);
        long version = created.get("version").asLong();

        mockMvc.perform(get("/api/v1/admin/factory-arrivals?status=COMMUNICATED")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(created.get("id").asText()))
                .andExpect(jsonPath("$[0].lifecycleStatus").value("COMMUNICATED"));

        mockMvc.perform(post("/api/v1/driver/requests/{id}/arrival", created.get("id").asText())
                        .header("Authorization", bearer(otherDriverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualArrivalAt": "2026-09-02T09:10:00+01:00",
                                  "version": %d
                                }
                                """.formatted(version)))
                .andExpect(status().isForbidden());

        String arrivedJson = mockMvc.perform(post("/api/v1/admin/factory-arrivals/{id}/confirm", created.get("id").asText())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualArrivalAt": "2026-09-02T09:15:00+01:00",
                                  "actualReceivedWheelQuantity": 4,
                                  "acknowledgeDiscrepancy": false,
                                  "reason": "Receção física",
                                  "version": %d
                                }
                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("AT_FACTORY"))
                .andExpect(jsonPath("$.arrivalConfirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.arrivalConfirmedBy").isNotEmpty())
                .andExpect(jsonPath("$.arrivalConfirmationSource").value("ADMIN"))
                .andExpect(jsonPath("$.quantityDiscrepancy").value(true))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/v1/admin/factory-arrivals/{id}/confirm", created.get("id").asText())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualArrivalAt": "2026-09-02T09:15:00+01:00",
                                  "version": %d
                                }
                                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("AT_FACTORY"))
                .andExpect(jsonPath("$.actualFactoryArrivalAt").value("2026-09-02T08:15:00Z"));

        mockMvc.perform(get("/api/v1/admin/factory-arrivals?status=COMMUNICATED")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(history.findByRequestIdOrderByChangedAtAsc(java.util.UUID.fromString(created.get("id").asText())))
                .anySatisfy(entry -> {
                    assertThat(entry.getNewStatus()).isEqualTo(pt.rucodel.productionplanning.domain.LifecycleStatus.AT_FACTORY);
                    assertThat(entry.getActorUserId()).isNotNull();
                    assertThat(users.existsById(entry.getActorUserId())).isTrue();
                });

        JsonNode ownArrival = createDriverRequest(driverToken, customerOne, 2);
        mockMvc.perform(post("/api/v1/driver/requests/{id}/arrival", ownArrival.get("id").asText())
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualArrivalAt": "2026-09-02T09:20:00+01:00",
                                  "version": %d
                                }
                                """.formatted(ownArrival.get("version").asLong())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("AT_FACTORY"))
                .andExpect(jsonPath("$.arrivalConfirmationSource").value("DRIVER"));

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
                .andExpect(status().is4xxClientError());
    }

    @Test
    void administratorCanEditArrivedRequestQuantitiesWithAuditAndCompletedFloor() throws Exception {
        String driverToken = login("driver1");
        String adminToken = login("admin");
        JsonNode created = createDriverRequest(driverToken, customerOne, 10);

        String arrived = mockMvc.perform(post("/api/v1/admin/factory-arrivals/{id}/confirm", created.get("id").asText())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actualArrivalAt":"2026-09-02T09:15:00+01:00","version":%d}
                                """.formatted(created.get("version").asLong())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        objectMapper.readTree(arrived);
        long version = requests.findById(java.util.UUID.fromString(created.get("id").asText())).orElseThrow().getVersion();
        mockMvc.perform(patch("/api/v1/admin/requests/{id}", created.get("id").asText())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "wheelQuantities": [
                                    {"type":"BIPARTITE","quantity":0},
                                    {"type":"WASHED","quantity":0},
                                    {"type":"NORMAL","quantity":11}
                                  ],
                                  "version": %d
                                }
                                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuantity").value(11));

        assertThat(audit.findByProductionSite_CodeOrderByCreatedAtDesc(ProductionSiteCode.PT, org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent()).anySatisfy(event -> {
            assertThat(event.getEventType()).isEqualTo("REQUEST_QUANTITY_CORRECTED");
            assertThat(event.getDetail()).contains("NORMAL=10", "NORMAL=11");
        });

        WheelIntakeRequestEntity saved = requests.findById(java.util.UUID.fromString(created.get("id").asText())).orElseThrow();
        saved.addCompletedWheelQuantities(java.util.Map.of(WheelType.NORMAL, 5));
        saved = requests.saveAndFlush(saved);

        mockMvc.perform(patch("/api/v1/admin/requests/{id}", saved.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "wheelQuantities": [
                                    {"type":"BIPARTITE","quantity":0},
                                    {"type":"WASHED","quantity":0},
                                    {"type":"NORMAL","quantity":4}
                                  ],
                                  "version": %d
                                }
                                """.formatted(saved.getVersion())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("REQUEST_QUANTITY_BELOW_COMPLETED"));
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
        return login(username, "PT");
    }

    private String login(String username, String productionSite) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"password","productionSite":"%s"}
                                """.formatted(username, productionSite)))
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
        customer.setProductionSite(portugal);
        return customer(customer, externalId, name);
    }

    private CustomerReferenceEntity customer(ProductionSiteEntity site, String externalId, String name) {
        CustomerReferenceEntity customer = new CustomerReferenceEntity();
        customer.setProductionSite(site);
        return customer(customer, externalId, name);
    }

    private CustomerReferenceEntity customer(CustomerReferenceEntity customer, String externalId, String name) {
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
        settings.setProductionSite(portugal);
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
