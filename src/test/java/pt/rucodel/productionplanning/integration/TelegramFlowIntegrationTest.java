package pt.rucodel.productionplanning.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pt.rucodel.productionplanning.domain.*;
import pt.rucodel.productionplanning.entity.*;
import pt.rucodel.productionplanning.repository.*;
import pt.rucodel.productionplanning.service.DashboardEventPublisher;
import pt.rucodel.productionplanning.telegram.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TelegramFlowIntegrationTest {
    @jakarta.annotation.Resource MockMvc mockMvc;
    @jakarta.annotation.Resource TelegramUpdateProcessor processor;
    @jakarta.annotation.Resource FakeTelegramBotClient bot;
    @jakarta.annotation.Resource DriverRepository drivers;
    @jakarta.annotation.Resource CustomerReferenceRepository customers;
    @jakarta.annotation.Resource WheelIntakeRequestRepository requests;
    @jakarta.annotation.Resource TelegramConversationRepository conversations;
    @jakarta.annotation.Resource TelegramIntakeDraftRepository drafts;
    @jakarta.annotation.Resource ConversationCustomerCandidateRepository customerCandidates;
    @jakarta.annotation.Resource CustomerRegistrationRequestRepository customerRegistrationRequests;
    @jakarta.annotation.Resource TelegramInboundUpdateRepository inboundUpdates;
    @jakarta.annotation.Resource MessagingIdentityRepository messagingIdentities;
    @jakarta.annotation.Resource MessagingIdentityEventRepository messagingIdentityEvents;
    @jakarta.annotation.Resource CapacityAlertRepository capacityAlerts;
    @jakarta.annotation.Resource DailyProductionSettingsRepository settings;
    @jakarta.annotation.Resource ProductionPlanRepository plans;
    @jakarta.annotation.Resource ProductionPlanItemRepository planItems;
    @jakarta.annotation.Resource RequestStatusHistoryRepository history;
    @jakarta.annotation.Resource PlanningAuditEventRepository audit;
    @jakarta.annotation.Resource WhatsAppIngestionItemRepository whatsapp;
    @jakarta.annotation.Resource JdbcTemplate jdbcTemplate;
    @SpyBean DashboardEventPublisher dashboardEvents;

    private CustomerReferenceEntity customer;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS customer_number_seq START WITH 1000 INCREMENT BY 1");
        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS driver_code_seq START WITH 1 INCREMENT BY 1");
        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS customer_code_seq START WITH 1 INCREMENT BY 1");
        bot.clear();
        customerCandidates.deleteAll();
        inboundUpdates.deleteAll();
        messagingIdentityEvents.deleteAll();
        capacityAlerts.deleteAll();
        planItems.deleteAll();
        plans.deleteAll();
        history.deleteAll();
        audit.deleteAll();
        whatsapp.deleteAll();
        conversations.findAll().forEach(conversation -> {
            conversation.setActiveDraft(null);
            conversations.save(conversation);
        });
        conversations.flush();
        drafts.deleteAll();
        requests.deleteAll();
        customerRegistrationRequests.deleteAll();
        conversations.deleteAll();
        messagingIdentities.deleteAll();
        settings.deleteAll();
        customers.deleteAll();
        drivers.deleteAll();

        customer = customers.save(customer("C1001", "Oficina Central Braga"));
        settings.save(settings(LocalDate.of(2099, 9, 10), 20));
    }

    @Test
    void newTelegramUserCreatesDriverAndDoesNotCreateDuplicatesWhenUsernameChanges() {
        process(1, 9001L, 7001L, "olduser", "/start");

        assertThat(bot.last()).contains("Bem-vindo ao Rucodel Bot", "qual é o seu nome");
        assertThat(drivers.findByTelegramUserId(9001L)).isEmpty();
        MessagingIdentityEntity identity = messagingIdentities
                .findByChannelAndIntegrationKeyAndExternalUserId(MessagingChannel.TELEGRAM, "RucodelPlanBot", "9001")
                .orElseThrow();
        assertThat(identity.getDriver()).isNull();
        assertThat(identity.getOnboardingStatus()).isEqualTo(MessagingIdentityOnboardingStatus.AWAITING_DRIVER_NAME);

        process(2, 9001L, 7001L, "olduser", "  João   Martins ");

        DriverEntity driver = drivers.findByTelegramUserId(9001L).orElseThrow();
        assertThat(driver.getName()).isEqualTo("João Martins");
        assertThat(driver.getTelegramChatId()).isEqualTo(7001L);
        assertThat(driver.getTelegramUsername()).isEqualTo("olduser");
        identity = messagingIdentities
                .findByChannelAndIntegrationKeyAndExternalUserId(MessagingChannel.TELEGRAM, "RucodelPlanBot", "9001")
                .orElseThrow();
        assertThat(identity.getDriver().getId()).isEqualTo(driver.getId());
        assertThat(identity.getOnboardingStatus()).isEqualTo(MessagingIdentityOnboardingStatus.COMPLETED);
        assertThat(bot.messages()).anySatisfy(message -> assertThat(message.text()).contains("Obrigado, João Martins", "registo foi concluído"));
        assertThat(bot.last()).contains("1/10 — Qual é o cliente?");

        process(3, 9001L, 7001L, "newuser", "/start");

        assertThat(drivers.findAll()).hasSize(1);
        assertThat(messagingIdentities.findAll()).hasSize(1);
        assertThat(drivers.findByTelegramUserId(9001L).orElseThrow().getTelegramUsername()).isEqualTo("newuser");
        assertThat(messagingIdentities.findByChannelAndIntegrationKeyAndExternalUserId(MessagingChannel.TELEGRAM, "RucodelPlanBot", "9001")
                .orElseThrow().getExternalUsername()).isEqualTo("newuser");
        assertThat(bot.messages()).anySatisfy(message -> assertThat(message.text()).contains("Vamos retomar"));
    }

    @Test
    void fullQuestionFlowValidatesAnswersAndCreatesRequestOnlyAfterConfirmation() {
        registerDriver(9101L, 7101L);

        process(10, 9101L, 7101L, "driver", "Oficina Central Braga");
        assertThat(bot.last()).contains("2/10 — Quantas jantes bipartidas");

        process(11, 9101L, 7101L, "driver", "-1");
        assertThat(bot.last()).contains("maior ou igual a zero", "2/10");
        process(12, 9101L, 7101L, "driver", "4");
        assertThat(bot.last()).contains("3/10 — Quantas jantes lavadas");
        process(13, 9101L, 7101L, "driver", "texto");
        assertThat(bot.last()).contains("maior ou igual a zero", "3/10");
        process(14, 9101L, 7101L, "driver", "6");
        assertThat(bot.last()).contains("4/10 — Quantas jantes normais");
        process(15, 9101L, 7101L, "driver", "15");
        assertThat(bot.last()).contains("5/10 — Em que data");

        process(16, 9101L, 7101L, "driver", "31/02/2099");
        assertThat(bot.last()).contains("A data é inválida");
        process(17, 9101L, 7101L, "driver", "10/09/2099");
        assertThat(bot.last()).contains("6/10 — Entre que horas");

        process(18, 9101L, 7101L, "driver", "4");
        assertThat(bot.last()).contains("Escolha apenas 1, 2 ou 3");
        process(19, 9101L, 7101L, "driver", "1");
        assertThat(bot.last()).contains("7/10 — Em que dia");

        process(20, 9101L, 7101L, "driver", "09/09/2099");
        assertThat(bot.last()).contains("não pode ser anterior");
        process(21, 9101L, 7101L, "driver", "10/09/2099");
        assertThat(bot.last()).contains("8/10 — Entre que horas");

        process(22, 9101L, 7101L, "driver", "1");
        assertThat(bot.last()).contains("9/10 — Alguma nota extra");

        process(23, 9101L, 7101L, "driver", "Não");
        assertThat(bot.last()).contains("10/10 — Confirma o pedido?", "Bipartidas: 4", "Lavadas: 6", "Normais: 15", "Total: 25 jantes", "Notas: Sem notas");
        assertThat(requests.findAll()).isEmpty();

        process(23, 9101L, 7101L, "driver", "Não");
        assertThat(requests.findAll()).isEmpty();

        callback(24, 9101L, 7101L, "Confirmar pedido");
        assertThat(bot.last()).contains("Pedido comunicado com sucesso", "pendente de confirmação de entrada na fábrica");
        assertThat(requests.findAll()).hasSize(1);
        WheelIntakeRequestEntity request = requests.findAll().getFirst();
        assertThat(request.getExpectedWheelQuantity()).isEqualTo(25);
        assertThat(request.getLifecycleStatus()).isEqualTo(LifecycleStatus.COMMUNICATED);
        assertThat(request.wheelQuantity(WheelType.BIPARTITE)).isEqualTo(4);
        assertThat(request.wheelQuantity(WheelType.WASHED)).isEqualTo(6);
        assertThat(request.wheelQuantity(WheelType.NORMAL)).isEqualTo(15);
        assertThat(request.getSource()).isEqualTo(RequestSource.TELEGRAM);
        assertThat(request.getSubmittedByIdentity()).isNotNull();
        assertThat(messagingIdentities.findById(request.getSubmittedByIdentity().getId()).orElseThrow().getExternalUserId()).isEqualTo("9101");
        assertThat(request.getFactoryDropoffSlot()).isEqualTo(FactoryTimeSlot.MORNING_09_14);
        assertThat(request.getFactoryPickupSlot()).isEqualTo(FactoryTimeSlot.MORNING_09_14);
        assertThat(request.getNotes()).isNull();
        ProductionPlanEntity plan = plans.findFirstByPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(LocalDate.of(2099, 9, 10))
                .orElseThrow();
        assertThat(planItems.findByPlanIdOrderByPriorityScoreAsc(plan.getId()))
                .anySatisfy(line -> {
                    assertThat(line.getRequest().getId()).isEqualTo(request.getId());
                    assertThat(line.getQuantity()).isEqualTo(21);
                    assertThat(line.plannedWheelQuantityMap().get(WheelType.BIPARTITE)).isZero();
                    assertThat(line.plannedWheelQuantityMap().get(WheelType.WASHED)).isEqualTo(6);
                    assertThat(line.plannedWheelQuantityMap().get(WheelType.NORMAL)).isEqualTo(15);
                });
        verify(dashboardEvents, atLeastOnce()).publishPlanUpdated(LocalDate.of(2099, 9, 10));

        callback(25, 9101L, 7101L, "Confirmar pedido");
        assertThat(requests.findAll()).hasSize(1);
        assertThat(bot.last()).contains("já foi tratado");
    }

    @Test
    void customerExactMatchUsesNormalizedNameAndContinuesToWheelTypes() {
        registerDriver(9151L, 7151L);

        process(110, 9151L, 7151L, "driver", " OFICINA   CENTRAL BRAGA ");

        assertThat(bot.messages()).anySatisfy(message -> assertThat(message.text()).contains("Cliente identificado: Oficina Central Braga"));
        assertThat(bot.last()).contains("2/10 — Quantas jantes bipartidas");
        TelegramIntakeDraftEntity draft = drafts.findFirstByDriverIdAndStatusOrderByCreatedAtDesc(
                drivers.findByTelegramUserId(9151L).orElseThrow().getId(), TelegramDraftStatus.ACTIVE).orElseThrow();
        assertThat(draft.getCustomer().getId()).isEqualTo(customer.getId());
        assertThat(draft.getCustomerRegistrationRequest()).isNull();
    }

    @Test
    void customerTypoShowsPersistedCandidatesAndSelectionUsesDisplayedPosition() {
        CustomerReferenceEntity first = customers.save(customer("C2001", "Loja de Reparação de Jantes de Ermesinde"));
        customers.save(customer("C2002", "Reparação de Jantes Ermesinde"));
        customers.save(customer("C2003", "Jantes de Ermesinde"));
        registerDriver(9152L, 7152L);

        process(120, 9152L, 7152L, "driver", "Loja de reparação de jantes de Irmezinde");

        assertThat(bot.last()).contains("Não encontrei um cliente com esse nome exato", "Criar novo cliente");
        TelegramConversationEntity conversation = conversations.findByTelegramUserId(9152L).orElseThrow();
        List<ConversationCustomerCandidateEntity> options = customerCandidates.findByConversationIdOrderByPositionAsc(conversation.getId());
        assertThat(options).isNotEmpty();
        assertThat(options.getLast().getOptionType()).isEqualTo(ConversationCustomerOptionType.CREATE_NEW_CUSTOMER);
        assertThat(options.getFirst().getCustomer().getId()).isEqualTo(first.getId());

        callback(121, 9152L, 7152L, "CUSTOMER_OPTION:1");

        assertThat(bot.messages()).anySatisfy(message -> assertThat(message.text()).contains("Cliente selecionado: Loja de Reparação de Jantes de Ermesinde"));
        assertThat(bot.last()).contains("2/10 — Quantas jantes bipartidas");
        TelegramIntakeDraftEntity draft = drafts.findFirstByDriverIdAndStatusOrderByCreatedAtDesc(
                drivers.findByTelegramUserId(9152L).orElseThrow().getId(), TelegramDraftStatus.ACTIVE).orElseThrow();
        assertThat(draft.getCustomer().getId()).isEqualTo(first.getId());
    }

    @Test
    void unknownCustomerRequiresConfirmationAndCreatesPendingRegistrationWithoutFakeData() {
        registerDriver(9153L, 7153L);

        process(130, 9153L, 7153L, "driver", "Cliente Lunar Desconhecido");
        assertThat(bot.last()).contains("Não encontrei nenhum cliente semelhante", "1 — Criar novo cliente", "2 — Corrigir");

        process(131, 9153L, 7153L, "driver", "1");
        assertThat(bot.last()).contains("Pretende criar um novo cliente com o nome", "Cliente Lunar Desconhecido");

        callback(132, 9153L, 7153L, "CUSTOMER_NEW_CONFIRM");
        assertThat(bot.last()).contains("Qual é o NIF ou VAT number do novo cliente?");

        process(133, 9153L, 7153L, "driver", "");
        assertThat(bot.last()).contains("O NIF/VAT é obrigatório");
        process(134, 9153L, 7153L, "driver", "123456789");
        assertThat(bot.last()).contains("Qual é o país do novo cliente?");
        callback(135, 9153L, 7153L, "COUNTRY:PT");
        assertThat(bot.last()).contains("Qual é a localidade do novo cliente?");
        process(136, 9153L, 7153L, "driver", " Braga ");
        assertThat(bot.last()).contains(
                "Confirme os dados do novo cliente",
                "Cliente Lunar Desconhecido",
                "Número de cliente RucoPlan:",
                "NIF/VAT: 123456789",
                "País: Portugal",
                "Localidade: Braga"
        );
        assertThat(bot.last()).doesNotContain("pendente de validação administrativa");
        assertThat(requests.findAll()).isEmpty();

        process(137, 9153L, 7153L, "driver", "1");
        assertThat(bot.last()).contains("2/10 — Quantas jantes bipartidas");
        assertThat(customerRegistrationRequests.findAll()).hasSize(1);
        CustomerRegistrationRequestEntity registration = customerRegistrationRequests.findAll().getFirst();
        assertThat(registration.getStatus()).isEqualTo(CustomerRegistrationStatus.APPROVED);
        assertThat(registration.getProposedName()).isEqualTo("Cliente Lunar Desconhecido");
        assertThat(registration.getReservedCustomerNumber()).isNotNull();
        assertThat(registration.getTaxIdentifier()).isEqualTo("123456789");
        assertThat(registration.getCountryCode()).isEqualTo("PT");
        assertThat(registration.getLocality()).isEqualTo("Braga");
        TelegramIntakeDraftEntity draft = drafts.findFirstByDriverIdAndStatusOrderByCreatedAtDesc(
                drivers.findByTelegramUserId(9153L).orElseThrow().getId(), TelegramDraftStatus.ACTIVE).orElseThrow();
        assertThat(draft.getCustomer()).isNotNull();
        CustomerReferenceEntity createdCustomer = customers.findById(draft.getCustomer().getId()).orElseThrow();
        assertThat(createdCustomer.getCustomerNumber()).isEqualTo(registration.getReservedCustomerNumber());
        assertThat(createdCustomer.getExternalCustomerId()).isNull();
        assertThat(draft.getCustomerRegistrationRequest()).isNull();
    }

    @Test
    void cancellingDraftKeepsConversationsIsolatedAndInactiveDriverCannotCreateRequest() {
        registerDriver(9201L, 7201L);
        registerDriver(9202L, 7202L);

        process(30, 9201L, 7201L, "driver1", "Oficina Central Braga");
        process(31, 9202L, 7202L, "driver2", "Oficina Central Braga");
        process(32, 9201L, 7201L, "driver1", "/cancelar");

        assertThat(conversations.findByTelegramUserId(9201L).orElseThrow().getState()).isEqualTo(TelegramConversationState.IDLE);
        assertThat(conversations.findByTelegramUserId(9202L).orElseThrow().getState()).isEqualTo(TelegramConversationState.AWAITING_BIPARTITE_QUANTITY);

        DriverEntity inactive = drivers.findByTelegramUserId(9201L).orElseThrow();
        inactive.setActive(false);
        drivers.saveAndFlush(inactive);

        process(33, 9201L, 7201L, "driver1", "/novo");
        assertThat(bot.last()).contains("desativado");
    }

    @Test
    void zeroQuantitiesAcrossAllWheelTypesAreRejectedAndDraftReturnsToBipartiteQuestion() {
        registerDriver(9301L, 7301L);

        process(40, 9301L, 7301L, "driver", "Oficina Central Braga");
        process(41, 9301L, 7301L, "driver", "0");
        process(42, 9301L, 7301L, "driver", "0");
        process(43, 9301L, 7301L, "driver", "0");

        assertThat(bot.last()).contains("O pedido tem de incluir pelo menos uma jante", "2/10 — Quantas jantes bipartidas");
        assertThat(conversations.findByTelegramUserId(9301L).orElseThrow().getState())
                .isEqualTo(TelegramConversationState.AWAITING_BIPARTITE_QUANTITY);
        assertThat(requests.findAll()).isEmpty();
    }

    @Test
    void onboardingRejectsInvalidNameAcceptsPortugueseNameProfileCommandAndRejectsAnotherPersonsContact() {
        process(50, 9501L, 7501L, null, "/start");
        process(51, 9501L, 7501L, null, "A");
        assertThat(bot.last()).contains("entre 2 e 120");

        process(52, 9501L, 7501L, null, "  Maria d'Ávila-Santos  ");
        DriverEntity driver = drivers.findByTelegramUserId(9501L).orElseThrow();
        assertThat(driver.getName()).isEqualTo("Maria d'Ávila-Santos");

        process(53, 9501L, 7501L, "changed_profile", "/perfil Maria João");
        assertThat(drivers.findById(driver.getId()).orElseThrow().getName()).isEqualTo("Maria João");
        assertThat(drivers.findById(driver.getId()).orElseThrow().getTelegramFirstName()).isEqualTo("Nome");

        contact(54, 9501L, 7501L, 9999L, "+351 912 345 678");
        assertThat(bot.messages()).anySatisfy(message -> assertThat(message.text()).contains("contacto de outra pessoa"));
        assertThat(messagingIdentities.findByChannelAndIntegrationKeyAndExternalUserId(MessagingChannel.TELEGRAM, "RucodelPlanBot", "9501")
                .orElseThrow().getPhoneNumber()).isNull();
    }

    @Test
    void webhookRejectsMissingOrInvalidSecretAndAcceptsConfiguredSecret() throws Exception {
        String payload = """
                {"update_id": 400, "message": {"message_id": 1, "text": "/start",
                "from": {"id": 9401, "username": "driver"},
                "chat": {"id": 7401, "type": "private"}}}
                """;

        mockMvc.perform(post("/api/v1/integrations/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/integrations/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/integrations/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", "test-telegram-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    private void registerDriver(Long telegramUserId, Long chatId) {
        process(1000 + telegramUserId.intValue(), telegramUserId, chatId, "driver", "/start");
        process(2000 + telegramUserId.intValue(), telegramUserId, chatId, "driver", "Motorista Teste");
        bot.clear();
    }

    private void process(long updateId, Long telegramUserId, Long chatId, String username, String text) {
        processor.process(new TelegramUpdate(updateId,
                new TelegramMessage(updateId, new TelegramUser(telegramUserId, username, "Nome", "Apelido"),
                        new TelegramChat(chatId, "private"), text),
                null));
    }

    private void callback(long updateId, Long telegramUserId, Long chatId, String data) {
        processor.process(new TelegramUpdate(updateId, null,
                new TelegramCallbackQuery("cb-" + updateId, new TelegramUser(telegramUserId, "driver", "Nome", "Apelido"),
                        new TelegramMessage(updateId, new TelegramUser(telegramUserId, "driver", "Nome", "Apelido"),
                                new TelegramChat(chatId, "private"), null),
                        data)));
    }

    private void contact(long updateId, Long telegramUserId, Long chatId, Long contactUserId, String phone) {
        processor.process(new TelegramUpdate(updateId,
                new TelegramMessage(updateId, new TelegramUser(telegramUserId, "driver", "Nome", "Apelido"),
                        new TelegramChat(chatId, "private"), null,
                        new TelegramContact(phone, "Outro", "Contacto", contactUserId)),
                null));
    }

    private CustomerReferenceEntity customer(String externalId, String name) {
        CustomerReferenceEntity entity = new CustomerReferenceEntity();
        entity.setExternalId(externalId);
        if (externalId != null && externalId.matches("C[0-9]+")) {
            entity.setCustomerNumber(Integer.parseInt(externalId.substring(1)));
        }
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

    @TestConfiguration
    static class TelegramTestConfig {
        @Bean
        @Primary
        FakeTelegramBotClient fakeTelegramBotClient() {
            return new FakeTelegramBotClient();
        }
    }

    static class FakeTelegramBotClient implements TelegramBotClient {
        private final List<SentMessage> messages = new ArrayList<>();
        private final List<String> answeredCallbacks = new ArrayList<>();

        @Override
        public void sendMessage(Long chatId, String text) {
            messages.add(new SentMessage(chatId, text));
        }

        @Override
        public void sendMessage(Long chatId, String text, List<List<TelegramButton>> inlineKeyboard) {
            messages.add(new SentMessage(chatId, text));
        }

        @Override
        public void answerCallbackQuery(String callbackQueryId) {
            answeredCallbacks.add(callbackQueryId);
        }

        String last() {
            return messages.getLast().text();
        }

        List<SentMessage> messages() {
            return messages;
        }

        void clear() {
            messages.clear();
            answeredCallbacks.clear();
        }
    }

    record SentMessage(Long chatId, String text) {
    }
}
