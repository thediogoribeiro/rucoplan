package pt.rucodel.productionplanning.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.*;
import pt.rucodel.productionplanning.entity.*;
import pt.rucodel.productionplanning.repository.*;
import pt.rucodel.productionplanning.service.DriverRegistrationService;
import pt.rucodel.productionplanning.service.DriverIntakeConversationService;
import pt.rucodel.productionplanning.service.CustomerRegistrationRequestService;
import pt.rucodel.productionplanning.service.CustomerResolutionResult;
import pt.rucodel.productionplanning.service.CustomerResolutionResultType;
import pt.rucodel.productionplanning.service.CustomerResolutionService;
import pt.rucodel.productionplanning.service.CustomerSearchResult;
import pt.rucodel.productionplanning.service.MessagingIdentityService;
import pt.rucodel.productionplanning.service.TelegramIdentitySnapshot;
import pt.rucodel.productionplanning.service.WheelIntakeRequestService;

import java.text.Normalizer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TelegramUpdateProcessor {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final int NOTES_LIMIT = 1000;

    private final TelegramInboundUpdateRepository inboundUpdates;
    private final TelegramConversationRepository conversations;
    private final TelegramIntakeDraftRepository drafts;
    private final DriverRepository drivers;
    private final CustomerReferenceRepository customerReferences;
    private final WheelIntakeRequestRepository requests;
    private final WheelIntakeRequestService intakeRequests;
    private final TelegramBotClient botClient;
    private final DriverIntakeConversationService conversationFlow;
    private final CustomerResolutionService customerResolution;
    private final CustomerRegistrationRequestService customerRegistrationRequests;
    private final MessagingIdentityService messagingIdentities;
    private final DriverRegistrationService driverRegistration;
    private final Clock clock;
    private final ZoneId businessZone;
    private final LocalTime overnightEndTime;

    public TelegramUpdateProcessor(TelegramInboundUpdateRepository inboundUpdates,
                                   TelegramConversationRepository conversations,
                                   TelegramIntakeDraftRepository drafts,
                                   DriverRepository drivers,
                                   CustomerReferenceRepository customerReferences,
                                   WheelIntakeRequestRepository requests,
                                   WheelIntakeRequestService intakeRequests,
                                   TelegramBotClient botClient,
                                   DriverIntakeConversationService conversationFlow,
                                   CustomerResolutionService customerResolution,
                                   CustomerRegistrationRequestService customerRegistrationRequests,
                                   MessagingIdentityService messagingIdentities,
                                   DriverRegistrationService driverRegistration,
                                   Clock clock,
                                   pt.rucodel.productionplanning.service.AppProperties appProperties,
                                   @Value("${app.planning.overnight-end-time:06:00}") String overnightEndTime) {
        this.inboundUpdates = inboundUpdates;
        this.conversations = conversations;
        this.drafts = drafts;
        this.drivers = drivers;
        this.customerReferences = customerReferences;
        this.requests = requests;
        this.intakeRequests = intakeRequests;
        this.botClient = botClient;
        this.conversationFlow = conversationFlow;
        this.customerResolution = customerResolution;
        this.customerRegistrationRequests = customerRegistrationRequests;
        this.messagingIdentities = messagingIdentities;
        this.driverRegistration = driverRegistration;
        this.clock = clock;
        this.businessZone = ZoneId.of(appProperties.timezone());
        this.overnightEndTime = LocalTime.parse(overnightEndTime);
    }

    @Transactional
    public void process(TelegramUpdate update) {
        if (update == null || update.updateId() == null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        TelegramInboundUpdateEntity inbound = inboundUpdates.findWithLockByUpdateId(update.updateId())
                .orElseGet(() -> {
                    TelegramInboundUpdateEntity created = new TelegramInboundUpdateEntity();
                    created.setUpdateId(update.updateId());
                    created.setReceivedAt(now);
                    return inboundUpdates.saveAndFlush(created);
                });
        if (inbound.getStatus() == TelegramInboundProcessingStatus.COMPLETED) {
            return;
        }
        inbound.setStatus(TelegramInboundProcessingStatus.PROCESSING);
        inbound.setAttempts(inbound.getAttempts() + 1);
        try {
            processOnce(update);
            inbound.setStatus(TelegramInboundProcessingStatus.COMPLETED);
            inbound.setProcessedAt(now);
            inbound.setErrorSummary(null);
        } catch (RuntimeException ex) {
            inbound.setStatus(TelegramInboundProcessingStatus.FAILED);
            inbound.setErrorSummary(safeError(ex));
            throw ex;
        }
    }

    private void processOnce(TelegramUpdate update) {
        TelegramCallbackQuery callback = update.callbackQuery();
        TelegramMessage message = update.message() != null ? update.message() : callback == null ? null : callback.message();
        TelegramUser from = update.message() != null ? update.message().from() : callback == null ? null : callback.from();
        if (callback != null) {
            botClient.answerCallbackQuery(callback.id());
        }
        if (message == null || from == null || from.id() == null || message.chat() == null || message.chat().id() == null) {
            return;
        }
        Long chatId = message.chat().id();
        if (!"private".equalsIgnoreCase(message.chat().type())) {
            botClient.sendMessage(chatId, "Por favor abra uma conversa privada com o bot para registar pedidos.");
            return;
        }

        String text = callback == null ? nullToBlank(message.text()) : nullToBlank(callback.data());
        TelegramIdentitySnapshot identitySnapshot = new TelegramIdentitySnapshot(
                from.id(),
                chatId,
                from.username(),
                from.firstName(),
                from.lastName(),
                from.languageCode()
        );
        MessagingIdentityEntity identity = messagingIdentities.resolveTelegramIdentity(identitySnapshot);
        DriverEntity driver = identity.getDriver();
        if (driver == null) {
            driver = drivers.findByTelegramUserId(from.id()).orElse(null);
            if (driver != null) {
                identity.setDriver(driver);
                messagingIdentities.record(identity, MessagingIdentityEventType.IDENTITY_LINKED,
                        "SYSTEM", "LEGACY_TELEGRAM_USER_ID", "Linked from legacy driver telegram_user_id.");
            }
        }
        TelegramConversationEntity conversation = conversations.findWithLockByTelegramUserId(from.id())
                .orElseGet(() -> newConversation(from.id(), chatId));
        conversation.setTelegramChatId(chatId);
        conversation.setMessagingIdentity(identity);
        if (driver != null) {
            driverRegistration.refreshLegacyTelegramFields(driver, identitySnapshot);
            conversation.setDriver(driver);
            if (conversation.getState() == TelegramConversationState.AWAITING_DRIVER_NAME) {
                conversation.setState(TelegramConversationState.IDLE);
            }
        }

        String command = command(text);
        if (message.contact() != null) {
            handleOptionalContact(identity, from, chatId, message.contact(), conversation);
            return;
        }
        if ("ignorar".equals(normalizeCommandText(text))) {
            botClient.sendMessage(chatId, "Sem problema. Pode continuar sem partilhar contacto.");
            askForCurrentState(conversation, chatId);
            return;
        }
        if ("/ajuda".equals(command)) {
            botClient.sendMessage(chatId, helpText());
            return;
        }

        if (driver == null) {
            handleOnboarding(conversation, identity, identitySnapshot, chatId, text, command);
            return;
        }

        if (identity.getBlockedAt() != null || identity.getOnboardingStatus() == MessagingIdentityOnboardingStatus.BLOCKED) {
            botClient.sendMessage(chatId, "A sua ligação Telegram está bloqueada. Contacte o administrador para voltar a criar pedidos.");
            return;
        }

        if (!driver.isActive()) {
            botClient.sendMessage(chatId, "O seu registo de motorista está desativado. Contacte o administrador para voltar a criar pedidos.");
            return;
        }

        if ("/cancelar".equals(command)) {
            cancelDraft(conversation, chatId);
            return;
        }
        if ("/pedidos".equals(command)) {
            sendRecentRequests(driver, chatId);
            return;
        }
        if ("/perfil".equals(command)) {
            handleProfileCommand(driver, chatId, text);
            return;
        }
        if ("/start".equals(command)) {
            resumeOrStart(conversation, driver, chatId);
            return;
        }
        if ("/novo".equals(command)) {
            handleNewCommand(conversation, driver, chatId);
            return;
        }
        if (callback != null && conversation.getState() == TelegramConversationState.IDLE
                && conversation.getActiveDraft() == null
                && Set.of("confirmar pedido", "corrigir", "cancelar").contains(normalizeCommandText(text))) {
            botClient.sendMessage(chatId, "Este pedido já foi tratado. Use /novo para criar outro pedido.");
            return;
        }

        routeState(conversation, driver, chatId, text);
    }

    private TelegramConversationEntity newConversation(Long telegramUserId, Long chatId) {
        TelegramConversationEntity conversation = new TelegramConversationEntity();
        conversation.setTelegramUserId(telegramUserId);
        conversation.setTelegramChatId(chatId);
        conversation.setState(TelegramConversationState.AWAITING_DRIVER_NAME);
        conversation.setCreatedBy("TELEGRAM");
        conversation.setUpdatedBy("TELEGRAM");
        return conversation;
    }

    private void handleOnboarding(TelegramConversationEntity conversation, MessagingIdentityEntity identity,
                                  TelegramIdentitySnapshot snapshot, Long chatId,
                                  String text, String command) {
        conversation.setState(TelegramConversationState.AWAITING_DRIVER_NAME);
        if ("/start".equals(command) || text.isBlank() || command != null) {
            conversations.save(conversation);
            botClient.sendMessage(chatId, """
                    Bem-vindo ao Rucodel Bot.

                    Antes de começarmos, qual é o seu nome?""");
            return;
        }
        DriverEntity savedDriver;
        try {
            savedDriver = driverRegistration.registerFromTelegramName(identity, snapshot, text);
        } catch (pt.rucodel.productionplanning.exception.InvalidRequestException ex) {
            botClient.sendMessage(chatId, ex.getMessage() + "\n\nQual é o seu nome?");
            return;
        }
        conversation.setDriver(savedDriver);
        conversation.setState(TelegramConversationState.IDLE);
        conversations.save(conversation);
        botClient.sendMessage(chatId, "Obrigado, " + savedDriver.getName() + ". O seu registo foi concluído.");
        botClient.sendMessage(chatId,
                "Se pretender, pode partilhar o seu número de contacto. Esta informação é opcional.",
                List.of(List.of(
                        TelegramButton.requestContact("Partilhar contacto"),
                        new TelegramButton("Ignorar", "Ignorar")
                )));
        startDraft(conversation, savedDriver, chatId);
    }

    private void handleOptionalContact(MessagingIdentityEntity identity, TelegramUser from, Long chatId,
                                       TelegramContact contact, TelegramConversationEntity conversation) {
        if (contact.userId() != null && !contact.userId().equals(from.id())) {
            botClient.sendMessage(chatId, "Não posso aceitar o contacto de outra pessoa. Esta informação é opcional.");
            askForCurrentState(conversation, chatId);
            return;
        }
        String phone = normalizePhone(contact.phoneNumber());
        if (phone == null) {
            botClient.sendMessage(chatId, "Não consegui validar esse número. Pode continuar sem partilhar contacto.");
            askForCurrentState(conversation, chatId);
            return;
        }
        identity.setPhoneNumber(phone);
        identity.setUpdatedBy("TELEGRAM");
        messagingIdentities.record(identity, MessagingIdentityEventType.CONTACT_SHARED, "TELEGRAM", identity.getExternalUserId(), null);
        botClient.sendMessage(chatId, "Contacto guardado. Obrigado.");
        askForCurrentState(conversation, chatId);
    }

    private void handleProfileCommand(DriverEntity driver, Long chatId, String text) {
        String argument = text == null ? "" : text.replaceFirst("(?i)^/perfil(@\\w+)?", "").trim();
        if (argument.isBlank()) {
            botClient.sendMessage(chatId, "O nome registado é: " + driver.getName()
                    + "\n\nPara alterar, envie /perfil seguido do novo nome. Exemplo: /perfil João Martins");
            return;
        }
        try {
            String newName = driverRegistration.validateAndNormalizeName(argument);
            driver.setName(newName);
            driver.setUpdatedBy("TELEGRAM");
            drivers.save(driver);
            botClient.sendMessage(chatId, "Nome atualizado para: " + newName);
        } catch (pt.rucodel.productionplanning.exception.InvalidRequestException ex) {
            botClient.sendMessage(chatId, ex.getMessage());
        }
    }

    private void resumeOrStart(TelegramConversationEntity conversation, DriverEntity driver, Long chatId) {
        TelegramIntakeDraftEntity draft = activeDraft(conversation, driver);
        if (draft == null) {
            startDraft(conversation, driver, chatId);
            return;
        }
        conversation.setActiveDraft(draft);
        botClient.sendMessage(chatId, "Vamos retomar o pedido em curso.");
        askForCurrentState(conversation, chatId);
    }

    private void handleNewCommand(TelegramConversationEntity conversation, DriverEntity driver, Long chatId) {
        TelegramIntakeDraftEntity draft = activeDraft(conversation, driver);
        if (draft == null) {
            startDraft(conversation, driver, chatId);
            return;
        }
        conversation.setState(TelegramConversationState.AWAITING_NEW_DRAFT_CONFIRMATION);
        conversation.setActiveDraft(draft);
        botClient.sendMessage(chatId,
                "Já existe um rascunho em curso. Quer cancelar esse rascunho e começar um novo?",
                List.of(List.of(
                        new TelegramButton("Começar novo", "NEW_CONFIRM"),
                        new TelegramButton("Retomar", "NEW_RESUME")
                )));
    }

    private void routeState(TelegramConversationEntity conversation, DriverEntity driver, Long chatId, String text) {
        switch (conversation.getState()) {
            case IDLE -> startDraft(conversation, driver, chatId);
            case AWAITING_CUSTOMER, AWAITING_CUSTOMER_NAME -> handleCustomerName(conversation, chatId, text);
            case AWAITING_CUSTOMER_SELECTION -> handleCustomerSelection(conversation, chatId, text);
            case AWAITING_NEW_CUSTOMER_CONFIRMATION -> handleNewCustomerConfirmation(conversation, chatId, text);
            case AWAITING_NEW_CUSTOMER_DETAILS -> handleNewCustomerFinalConfirmation(conversation, chatId, text);
            case AWAITING_NEW_CUSTOMER_FINAL_CONFIRMATION -> handleNewCustomerFinalConfirmation(conversation, chatId, text);
            case AWAITING_BIPARTITE_QUANTITY -> handleQuantity(conversation, chatId, text, WheelType.BIPARTITE, TelegramConversationState.AWAITING_WASHED_QUANTITY);
            case AWAITING_WASHED_QUANTITY -> handleQuantity(conversation, chatId, text, WheelType.WASHED, TelegramConversationState.AWAITING_NORMAL_QUANTITY);
            case AWAITING_NORMAL_QUANTITY -> handleNormalQuantity(conversation, chatId, text);
            case AWAITING_FACTORY_DROPOFF_DATE -> handleDropoffDate(conversation, chatId, text);
            case AWAITING_FACTORY_DROPOFF_SLOT -> handleDropoffSlot(conversation, chatId, text);
            case AWAITING_READY_DATE -> handleReadyDate(conversation, chatId, text);
            case AWAITING_FACTORY_PICKUP_SLOT -> handlePickupSlot(conversation, chatId, text);
            case AWAITING_NOTES -> handleNotes(conversation, chatId, text);
            case AWAITING_CONFIRMATION -> handleConfirmation(conversation, chatId, text);
            case AWAITING_CORRECTION_FIELD -> handleCorrectionField(conversation, chatId, text);
            case AWAITING_NEW_DRAFT_CONFIRMATION -> handleNewDraftConfirmation(conversation, driver, chatId, text);
            case AWAITING_DRIVER_NAME -> botClient.sendMessage(chatId, "O seu registo já existe. Use /novo para criar um pedido.");
        }
    }

    private void startDraft(TelegramConversationEntity conversation, DriverEntity driver, Long chatId) {
        TelegramIntakeDraftEntity draft = new TelegramIntakeDraftEntity();
        draft.setDriver(driver);
        draft.setStatus(TelegramDraftStatus.ACTIVE);
        draft.setCreatedBy("TELEGRAM");
        draft.setUpdatedBy("TELEGRAM");
        TelegramIntakeDraftEntity saved = drafts.saveAndFlush(draft);
        conversation.setActiveDraft(saved);
        conversation.setState(TelegramConversationState.AWAITING_CUSTOMER_NAME);
        conversations.save(conversation);
        botClient.sendMessage(chatId, conversationFlow.customerQuestion());
    }

    private TelegramIntakeDraftEntity activeDraft(TelegramConversationEntity conversation, DriverEntity driver) {
        TelegramIntakeDraftEntity active = conversation.getActiveDraft();
        if (active != null && active.getStatus() == TelegramDraftStatus.ACTIVE) {
            return active;
        }
        return drafts.findFirstByDriverIdAndStatusOrderByCreatedAtDesc(driver.getId(), TelegramDraftStatus.ACTIVE)
                .orElse(null);
    }

    private TelegramIntakeDraftEntity requireDraft(TelegramConversationEntity conversation) {
        TelegramIntakeDraftEntity draft = conversation.getActiveDraft();
        if (draft == null || draft.getStatus() != TelegramDraftStatus.ACTIVE) {
            throw new IllegalStateException("Telegram draft is missing.");
        }
        return draft;
    }

    private void handleCustomerName(TelegramConversationEntity conversation, Long chatId, String text) {
        TelegramIntakeDraftEntity draft = requireDraft(conversation);
        CustomerResolutionResult result = customerResolution.resolveInitial(conversation, text);
        if (result.type() == CustomerResolutionResultType.INVALID) {
            botClient.sendMessage(chatId, result.error() + "\n\n" + conversationFlow.customerQuestion());
            return;
        }
        if (result.type() == CustomerResolutionResultType.EXACT) {
            assignCustomerAndAdvance(conversation, draft, result.exactCustomer(), chatId, "Cliente identificado: ");
            return;
        }
        conversation.setState(TelegramConversationState.AWAITING_CUSTOMER_SELECTION);
        conversations.save(conversation);
        List<ConversationCustomerCandidateEntity> options = customerResolution.currentOptions(conversation);
        botClient.sendMessage(chatId, customerOptionsText(options), customerOptionsKeyboard(options));
    }

    private void handleCustomerSelection(TelegramConversationEntity conversation, Long chatId, String text) {
        Integer position = parseOption(text);
        if (position == null) {
            repeatCustomerOptions(conversation, chatId, "Selecione uma opção usando apenas o número apresentado.");
            return;
        }
        ConversationCustomerCandidateEntity option = customerResolution.option(conversation, position).orElse(null);
        if (option == null) {
            repeatCustomerOptions(conversation, chatId, "Essa opção já não está disponível. Selecione uma das opções apresentadas.");
            return;
        }
        TelegramIntakeDraftEntity draft = requireDraft(conversation);
        if (option.getOptionType() == ConversationCustomerOptionType.EXISTING_CUSTOMER) {
            CustomerReferenceEntity customer = option.getCustomer();
            if (customer == null || !customer.isActive()) {
                repeatCustomerOptions(conversation, chatId, "Esse cliente já não está disponível. Escolha outra opção.");
                return;
            }
            assignCustomerAndAdvance(conversation, draft, customer, chatId, "Cliente selecionado: ");
            return;
        }
        if (option.getOptionType() == ConversationCustomerOptionType.CREATE_NEW_CUSTOMER) {
            conversation.setState(TelegramConversationState.AWAITING_NEW_CUSTOMER_CONFIRMATION);
            conversations.save(conversation);
            botClient.sendMessage(chatId, """
                    Pretende criar um novo cliente com o nome:

                    %s?""".formatted(option.getOriginalSearchText()),
                    List.of(List.of(
                            new TelegramButton("Confirmar nome", "CUSTOMER_NEW_CONFIRM"),
                            new TelegramButton("Corrigir nome", "CUSTOMER_NEW_CORRECT"),
                            new TelegramButton("Cancelar", "CUSTOMER_NEW_CANCEL")
                    )));
            return;
        }
        customerResolution.clearCandidates(conversation);
        conversation.setState(TelegramConversationState.AWAITING_CUSTOMER_NAME);
        conversations.save(conversation);
        botClient.sendMessage(chatId, conversationFlow.customerQuestion());
    }

    private void handleNewCustomerConfirmation(TelegramConversationEntity conversation, Long chatId, String text) {
        String normalized = normalizeCommandText(text);
        if (normalized.equals("customer_new_confirm") || normalized.equals("confirmar nome") || normalized.equals("confirmar") || normalized.equals("sim")) {
            conversation.setState(TelegramConversationState.AWAITING_NEW_CUSTOMER_FINAL_CONFIRMATION);
            conversations.save(conversation);
            botClient.sendMessage(chatId, newCustomerFinalMessage(conversation),
                    List.of(List.of(
                            new TelegramButton("Confirmar", "CUSTOMER_FINAL_CONFIRM"),
                            new TelegramButton("Corrigir", "CUSTOMER_NEW_CORRECT"),
                            new TelegramButton("Cancelar", "CUSTOMER_NEW_CANCEL")
                    )));
            return;
        }
        if (normalized.equals("customer_new_correct") || normalized.equals("corrigir nome") || normalized.equals("corrigir")) {
            customerResolution.clearCandidates(conversation);
            conversation.setState(TelegramConversationState.AWAITING_CUSTOMER_NAME);
            conversations.save(conversation);
            botClient.sendMessage(chatId, conversationFlow.customerQuestion());
            return;
        }
        if (normalized.equals("customer_new_cancel") || normalized.equals("cancelar")) {
            customerResolution.clearCandidates(conversation);
            conversation.setState(TelegramConversationState.AWAITING_CUSTOMER_NAME);
            conversations.save(conversation);
            botClient.sendMessage(chatId, "Criação de cliente cancelada.\n\n" + conversationFlow.customerQuestion());
            return;
        }
        botClient.sendMessage(chatId, "Responda com Confirmar nome, Corrigir nome ou Cancelar.");
    }

    private void handleNewCustomerFinalConfirmation(TelegramConversationEntity conversation, Long chatId, String text) {
        String normalized = normalizeCommandText(text);
        if (normalized.equals("customer_final_confirm") || normalized.equals("confirmar") || normalized.equals("1") || normalized.equals("sim")) {
            ConversationCustomerCandidateEntity createOption = customerResolution.currentOptions(conversation).stream()
                    .filter(option -> option.getOptionType() == ConversationCustomerOptionType.CREATE_NEW_CUSTOMER)
                    .findFirst()
                    .orElse(null);
            if (createOption == null) {
                conversation.setState(TelegramConversationState.AWAITING_CUSTOMER_NAME);
                conversations.save(conversation);
                botClient.sendMessage(chatId, "A pesquisa anterior expirou. Escreva novamente o nome do cliente.");
                return;
            }
            TelegramIntakeDraftEntity draft = requireDraft(conversation);
            CustomerRegistrationRequestEntity registration = customerRegistrationRequests.createPending(
                    createOption.getOriginalSearchText(),
                    draft.getDriver(),
                    conversation.getMessagingIdentity(),
                    conversation
            );
            draft.setCustomer(null);
            draft.setCustomerRegistrationRequest(registration);
            draft.setCustomerNameSnapshot(registration.getProposedName() + " (Pendente de validação)");
            draft.setCustomerCandidateIds(null);
            customerResolution.clearCandidates(conversation);
            drafts.save(draft);
            botClient.sendMessage(chatId, "Cliente registado como pendente de validação administrativa. O pedido pode continuar normalmente.");
            saveAndAdvanceOrSummarise(conversation, TelegramConversationState.AWAITING_BIPARTITE_QUANTITY,
                    conversationFlow.quantityQuestion(WheelType.BIPARTITE));
            return;
        }
        if (normalized.equals("customer_new_correct") || normalized.equals("corrigir") || normalized.equals("2")) {
            customerResolution.clearCandidates(conversation);
            conversation.setState(TelegramConversationState.AWAITING_CUSTOMER_NAME);
            conversations.save(conversation);
            botClient.sendMessage(chatId, conversationFlow.customerQuestion());
            return;
        }
        if (normalized.equals("customer_new_cancel") || normalized.equals("cancelar") || normalized.equals("3")) {
            customerResolution.clearCandidates(conversation);
            conversation.setState(TelegramConversationState.AWAITING_CUSTOMER_NAME);
            conversations.save(conversation);
            botClient.sendMessage(chatId, "Criação de cliente cancelada.\n\n" + conversationFlow.customerQuestion());
            return;
        }
        botClient.sendMessage(chatId, "Responda com 1 para confirmar, 2 para corrigir ou 3 para cancelar.");
    }

    private void assignCustomerAndAdvance(TelegramConversationEntity conversation, TelegramIntakeDraftEntity draft,
                                          CustomerReferenceEntity customer, Long chatId, String prefix) {
        draft.setCustomer(customer);
        draft.setCustomerRegistrationRequest(null);
        draft.setCustomerNameSnapshot(customer.getName());
        draft.setCustomerCandidateIds(null);
        customerResolution.clearCandidates(conversation);
        drafts.save(draft);
        botClient.sendMessage(chatId, prefix + customer.getName() + ".");
        saveAndAdvanceOrSummarise(conversation, TelegramConversationState.AWAITING_BIPARTITE_QUANTITY,
                conversationFlow.quantityQuestion(WheelType.BIPARTITE));
    }

    private String customerOptionsText(List<ConversationCustomerCandidateEntity> options) {
        if (options.isEmpty()) {
            return "A pesquisa expirou. Escreva novamente o nome do cliente.";
        }
        String original = options.getFirst().getOriginalSearchText();
        boolean hasExisting = options.stream().anyMatch(option -> option.getOptionType() == ConversationCustomerOptionType.EXISTING_CUSTOMER);
        boolean exactMultiple = options.stream()
                .filter(option -> option.getOptionType() == ConversationCustomerOptionType.EXISTING_CUSTOMER)
                .allMatch(option -> option.getSimilarityScore() != null && option.getSimilarityScore() >= 1.0);
        StringBuilder builder = new StringBuilder();
        if (hasExisting) {
            if (exactMultiple) {
                builder.append("Encontrei mais do que um cliente com esse nome.\n\nÉ algum destes clientes?\n\n");
            } else {
                builder.append("Não encontrei um cliente com esse nome exato.\n\nÉ algum destes clientes?\n\n");
            }
        } else {
            builder.append("Não encontrei nenhum cliente semelhante a \"")
                    .append(original)
                    .append("\".\n\n");
        }
        for (ConversationCustomerCandidateEntity option : options) {
            builder.append(option.getPosition()).append(" — ");
            if (option.getOptionType() == ConversationCustomerOptionType.EXISTING_CUSTOMER) {
                builder.append(option.getCustomerNameSnapshot());
                if (option.getCustomer() != null && option.getCustomer().getExternalId() != null && !option.getCustomer().getExternalId().isBlank()) {
                    builder.append(" — n.º ").append(option.getCustomer().getExternalId());
                }
            } else if (option.getOptionType() == ConversationCustomerOptionType.CREATE_NEW_CUSTOMER) {
                builder.append(hasExisting ? "Criar novo cliente: " : "Criar novo cliente com este nome");
                if (hasExisting) {
                    builder.append(option.getOriginalSearchText());
                }
            } else {
                builder.append("Corrigir o nome pesquisado");
            }
            builder.append("\n");
        }
        builder.append("\nSelecione uma opção.");
        return builder.toString();
    }

    private List<List<TelegramButton>> customerOptionsKeyboard(List<ConversationCustomerCandidateEntity> options) {
        return options.stream()
                .map(option -> List.of(new TelegramButton(Integer.toString(option.getPosition()), "CUSTOMER_OPTION:" + option.getPosition())))
                .toList();
    }

    private void repeatCustomerOptions(TelegramConversationEntity conversation, Long chatId, String warning) {
        List<ConversationCustomerCandidateEntity> options = customerResolution.currentOptions(conversation);
        botClient.sendMessage(chatId, warning + "\n\n" + customerOptionsText(options), customerOptionsKeyboard(options));
    }

    private Integer parseOption(String text) {
        String normalized = normalizeCommandText(text);
        if (normalized.startsWith("customer_option:")) {
            normalized = normalized.substring("customer_option:".length()).trim();
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String newCustomerFinalMessage(TelegramConversationEntity conversation) {
        ConversationCustomerCandidateEntity createOption = customerResolution.currentOptions(conversation).stream()
                .filter(option -> option.getOptionType() == ConversationCustomerOptionType.CREATE_NEW_CUSTOMER)
                .findFirst()
                .orElse(null);
        String name = createOption == null ? "Cliente sem nome" : createOption.getOriginalSearchText();
        return """
                Confirme os dados do novo cliente:

                Nome: %s
                Número de cliente: Não informado
                NIF/VAT: Não informado
                País: Não informado
                Localidade: Não informado

                Este cliente será registado como pendente de validação administrativa. O pedido de jantes pode continuar normalmente.

                1 — Confirmar
                2 — Corrigir
                3 — Cancelar""".formatted(name);
    }

    private void handleQuantity(TelegramConversationEntity conversation, Long chatId, String text, WheelType type,
                                TelegramConversationState nextState) {
        Integer quantity = conversationFlow.parseQuantity(text);
        if (quantity == null) {
            botClient.sendMessage(chatId, "A quantidade tem de ser um número inteiro maior ou igual a zero.\n\n"
                    + conversationFlow.quantityQuestion(type));
            return;
        }
        requireDraft(conversation).setWheelQuantity(type, quantity);
        saveAndAdvanceOrSummarise(conversation, nextState,
                conversationFlow.quantityQuestion(type == WheelType.BIPARTITE ? WheelType.WASHED : WheelType.NORMAL));
    }

    private void handleNormalQuantity(TelegramConversationEntity conversation, Long chatId, String text) {
        Integer quantity = conversationFlow.parseQuantity(text);
        if (quantity == null) {
            botClient.sendMessage(chatId, "A quantidade tem de ser um número inteiro maior ou igual a zero.\n\n"
                    + conversationFlow.quantityQuestion(WheelType.NORMAL));
            return;
        }
        TelegramIntakeDraftEntity draft = requireDraft(conversation);
        draft.setWheelQuantity(WheelType.NORMAL, quantity);
        if (draft.totalWheelQuantity() <= 0) {
            draft.clearWheelQuantities();
            drafts.save(draft);
            conversation.setState(TelegramConversationState.AWAITING_BIPARTITE_QUANTITY);
            conversations.save(conversation);
            botClient.sendMessage(chatId, "O pedido tem de incluir pelo menos uma jante. Vamos voltar a indicar as quantidades por tipo.\n\n"
                    + conversationFlow.quantityQuestion(WheelType.BIPARTITE));
            return;
        }
        saveAndAdvanceOrSummarise(conversation, TelegramConversationState.AWAITING_FACTORY_DROPOFF_DATE,
                "5/10 — Em que data serão deixadas na fábrica? Responda no formato DD/MM/AAAA.");
    }

    private void handleDropoffDate(TelegramConversationEntity conversation, Long chatId, String text) {
        LocalDate date = parseFutureDate(text, chatId, "5/10 — Em que data serão deixadas na fábrica? Responda no formato DD/MM/AAAA.");
        if (date == null) {
            return;
        }
        TelegramIntakeDraftEntity draft = requireDraft(conversation);
        draft.setFactoryDropoffDate(date);
        if (draft.getReadyDate() != null && draft.getReadyDate().isBefore(date)) {
            draft.setReadyDate(null);
            draft.setFactoryPickupSlot(null);
        }
        saveAndAdvanceOrSummarise(conversation, TelegramConversationState.AWAITING_FACTORY_DROPOFF_SLOT, dropoffSlotQuestion());
    }

    private void handleDropoffSlot(TelegramConversationEntity conversation, Long chatId, String text) {
        FactoryTimeSlot slot = FactoryTimeSlot.fromOption(text);
        if (slot == null) {
            botClient.sendMessage(chatId, "Escolha apenas 1, 2 ou 3.\n\n" + dropoffSlotQuestion());
            return;
        }
        TelegramIntakeDraftEntity draft = requireDraft(conversation);
        draft.setFactoryDropoffSlot(slot);
        if (draft.getReadyDate() != null
                && draft.getFactoryDropoffDate().equals(draft.getReadyDate())
                && draft.getFactoryPickupSlot() != null
                && draft.getFactoryPickupSlot().option() < slot.option()) {
            draft.setFactoryPickupSlot(null);
        }
        saveAndAdvanceOrSummarise(conversation, TelegramConversationState.AWAITING_READY_DATE,
                "7/10 — Em que dia têm de estar prontas? Responda no formato DD/MM/AAAA.");
    }

    private void handleReadyDate(TelegramConversationEntity conversation, Long chatId, String text) {
        LocalDate date = parseDate(text);
        TelegramIntakeDraftEntity draft = requireDraft(conversation);
        if (date == null) {
            botClient.sendMessage(chatId, "A data é inválida. Use o formato DD/MM/AAAA.\n\n7/10 — Em que dia têm de estar prontas? Responda no formato DD/MM/AAAA.");
            return;
        }
        if (date.isBefore(draft.getFactoryDropoffDate())) {
            botClient.sendMessage(chatId, "A data em que as jantes têm de estar prontas não pode ser anterior à data de entrada na fábrica.\n\n7/10 — Em que dia têm de estar prontas? Responda no formato DD/MM/AAAA.");
            return;
        }
        draft.setReadyDate(date);
        saveAndAdvanceOrSummarise(conversation, TelegramConversationState.AWAITING_FACTORY_PICKUP_SLOT, pickupSlotQuestion());
    }

    private void handlePickupSlot(TelegramConversationEntity conversation, Long chatId, String text) {
        FactoryTimeSlot slot = FactoryTimeSlot.fromOption(text);
        TelegramIntakeDraftEntity draft = requireDraft(conversation);
        if (slot == null) {
            botClient.sendMessage(chatId, "Escolha apenas 1, 2 ou 3.\n\n" + pickupSlotQuestion());
            return;
        }
        if (draft.getFactoryDropoffDate().equals(draft.getReadyDate())
                && slot.option() < draft.getFactoryDropoffSlot().option()) {
            botClient.sendMessage(chatId, "No mesmo dia, o horário de levantamento não pode ser anterior ao horário de entrada.\n\n" + pickupSlotQuestion());
            return;
        }
        draft.setFactoryPickupSlot(slot);
        saveAndAdvanceOrSummarise(conversation, TelegramConversationState.AWAITING_NOTES,
                "9/10 — Alguma nota extra? Escreva a nota ou responda ‘Não’.");
    }

    private void handleNotes(TelegramConversationEntity conversation, Long chatId, String text) {
        requireDraft(conversation).setNotes(normalizeNotes(text));
        conversation.setState(TelegramConversationState.AWAITING_CONFIRMATION);
        drafts.save(requireDraft(conversation));
        conversations.save(conversation);
        sendSummary(chatId, requireDraft(conversation));
    }

    private void handleConfirmation(TelegramConversationEntity conversation, Long chatId, String text) {
        String normalized = normalizeCommandText(text);
        if (normalized.equals("confirmar pedido") || normalized.equals("confirmar") || normalized.equals("sim") || normalized.equals("confirm")) {
            confirmDraft(conversation, chatId);
            return;
        }
        if (normalized.equals("corrigir") || normalized.equals("corrigir pedido")) {
            conversation.setState(TelegramConversationState.AWAITING_CORRECTION_FIELD);
            conversations.save(conversation);
            botClient.sendMessage(chatId, correctionQuestion());
            return;
        }
        if (normalized.equals("cancelar") || normalized.equals("cancelar pedido")) {
            cancelDraft(conversation, chatId);
            return;
        }
        botClient.sendMessage(chatId, "Responda com Confirmar pedido, Corrigir ou Cancelar.");
        sendSummary(chatId, requireDraft(conversation));
    }

    private void handleCorrectionField(TelegramConversationEntity conversation, Long chatId, String text) {
        TelegramIntakeDraftEntity draft = requireDraft(conversation);
        switch (text.trim()) {
            case "1" -> {
                draft.setCustomer(null);
                draft.setCustomerRegistrationRequest(null);
                draft.setCustomerNameSnapshot(null);
                draft.setCustomerCandidateIds(null);
                customerResolution.clearCandidates(conversation);
                conversation.setState(TelegramConversationState.AWAITING_CUSTOMER_NAME);
                botClient.sendMessage(chatId, conversationFlow.customerQuestion());
            }
            case "2" -> {
                draft.clearWheelQuantity(WheelType.BIPARTITE);
                conversation.setState(TelegramConversationState.AWAITING_BIPARTITE_QUANTITY);
                botClient.sendMessage(chatId, conversationFlow.quantityQuestion(WheelType.BIPARTITE));
            }
            case "3" -> {
                draft.clearWheelQuantity(WheelType.WASHED);
                conversation.setState(TelegramConversationState.AWAITING_WASHED_QUANTITY);
                botClient.sendMessage(chatId, conversationFlow.quantityQuestion(WheelType.WASHED));
            }
            case "4" -> {
                draft.clearWheelQuantity(WheelType.NORMAL);
                conversation.setState(TelegramConversationState.AWAITING_NORMAL_QUANTITY);
                botClient.sendMessage(chatId, conversationFlow.quantityQuestion(WheelType.NORMAL));
            }
            case "5" -> {
                draft.setFactoryDropoffDate(null);
                draft.setFactoryDropoffSlot(null);
                conversation.setState(TelegramConversationState.AWAITING_FACTORY_DROPOFF_DATE);
                botClient.sendMessage(chatId, "5/10 — Em que data serão deixadas na fábrica? Responda no formato DD/MM/AAAA.");
            }
            case "6" -> {
                draft.setFactoryDropoffSlot(null);
                conversation.setState(TelegramConversationState.AWAITING_FACTORY_DROPOFF_SLOT);
                botClient.sendMessage(chatId, dropoffSlotQuestion());
            }
            case "7" -> {
                draft.setReadyDate(null);
                draft.setFactoryPickupSlot(null);
                conversation.setState(TelegramConversationState.AWAITING_READY_DATE);
                botClient.sendMessage(chatId, "7/10 — Em que dia têm de estar prontas? Responda no formato DD/MM/AAAA.");
            }
            case "8" -> {
                draft.setFactoryPickupSlot(null);
                conversation.setState(TelegramConversationState.AWAITING_FACTORY_PICKUP_SLOT);
                botClient.sendMessage(chatId, pickupSlotQuestion());
            }
            case "9" -> {
                draft.setNotes(null);
                conversation.setState(TelegramConversationState.AWAITING_NOTES);
                botClient.sendMessage(chatId, "9/10 — Alguma nota extra? Escreva a nota ou responda ‘Não’.");
            }
            default -> botClient.sendMessage(chatId, "Escolha um campo entre 1 e 9.\n\n" + correctionQuestion());
        }
        drafts.save(draft);
        conversations.save(conversation);
    }

    private void handleNewDraftConfirmation(TelegramConversationEntity conversation, DriverEntity driver, Long chatId, String text) {
        String normalized = normalizeCommandText(text);
        if (normalized.equals("new_confirm") || normalized.equals("comecar novo") || normalized.equals("começar novo") || normalized.equals("sim")) {
            TelegramIntakeDraftEntity draft = activeDraft(conversation, driver);
            if (draft != null) {
                draft.setStatus(TelegramDraftStatus.CANCELLED);
                drafts.save(draft);
            }
            startDraft(conversation, driver, chatId);
            return;
        }
        if (normalized.equals("new_resume") || normalized.equals("retomar") || normalized.equals("nao") || normalized.equals("não")) {
            resumeOrStart(conversation, driver, chatId);
            return;
        }
        botClient.sendMessage(chatId, "Responda com Começar novo ou Retomar.");
    }

    private void confirmDraft(TelegramConversationEntity conversation, Long chatId) {
        TelegramIntakeDraftEntity draft = requireDraft(conversation);
        if (draft.getConfirmedRequest() != null) {
            botClient.sendMessage(chatId, "Este pedido já foi confirmado.");
            return;
        }
        if (!complete(draft)) {
            askForCurrentState(conversation, chatId);
            return;
        }
        OffsetDateTime dropoffStart = draft.getFactoryDropoffSlot().startAt(draft.getFactoryDropoffDate(), businessZone);
        OffsetDateTime dropoffEnd = draft.getFactoryDropoffSlot().endAt(draft.getFactoryDropoffDate(), businessZone, overnightEndTime);
        OffsetDateTime pickupStart = draft.getFactoryPickupSlot().startAt(draft.getReadyDate(), businessZone);
        OffsetDateTime pickupEnd = draft.getFactoryPickupSlot().endAt(draft.getReadyDate(), businessZone, overnightEndTime);
        WheelIntakeRequestEntity request = draft.getCustomer() == null
                ? intakeRequests.createFromTelegramWithPendingCustomer(
                "telegram-draft-" + draft.getId(),
                draft.getDriver(),
                conversation.getMessagingIdentity(),
                draft.getCustomerRegistrationRequest(),
                draft.wheelQuantityMap(),
                dropoffStart,
                dropoffEnd,
                draft.getFactoryDropoffSlot(),
                pickupStart,
                pickupEnd,
                draft.getFactoryPickupSlot(),
                draft.getNotes()
        )
                : intakeRequests.createFromTelegram(
                "telegram-draft-" + draft.getId(),
                draft.getDriver(),
                conversation.getMessagingIdentity(),
                draft.getCustomer(),
                draft.wheelQuantityMap(),
                dropoffStart,
                dropoffEnd,
                draft.getFactoryDropoffSlot(),
                pickupStart,
                pickupEnd,
                draft.getFactoryPickupSlot(),
                draft.getNotes()
        );
        draft.setConfirmedRequest(request);
        draft.setStatus(TelegramDraftStatus.CONFIRMED);
        drafts.save(draft);
        customerResolution.clearCandidates(conversation);
        conversation.setActiveDraft(null);
        conversation.setState(TelegramConversationState.IDLE);
        conversations.save(conversation);
        botClient.sendMessage(chatId, "Pedido confirmado. Obrigado.");
    }

    private void cancelDraft(TelegramConversationEntity conversation, Long chatId) {
        TelegramIntakeDraftEntity draft = conversation.getActiveDraft();
        if (draft != null && draft.getStatus() == TelegramDraftStatus.ACTIVE) {
            draft.setStatus(TelegramDraftStatus.CANCELLED);
            drafts.save(draft);
        }
        customerResolution.clearCandidates(conversation);
        conversation.setActiveDraft(null);
        conversation.setState(TelegramConversationState.IDLE);
        conversations.save(conversation);
        botClient.sendMessage(chatId, "Pedido em curso cancelado.");
    }

    private void saveAndAdvanceOrSummarise(TelegramConversationEntity conversation, TelegramConversationState nextState, String question) {
        TelegramIntakeDraftEntity draft = requireDraft(conversation);
        drafts.save(draft);
        if (nextState != TelegramConversationState.AWAITING_NOTES && complete(draft)) {
            conversation.setState(TelegramConversationState.AWAITING_CONFIRMATION);
            conversations.save(conversation);
            sendSummary(conversation.getTelegramChatId(), draft);
            return;
        }
        conversation.setState(nextState);
        conversations.save(conversation);
        botClient.sendMessage(conversation.getTelegramChatId(), question);
    }

    private boolean complete(TelegramIntakeDraftEntity draft) {
        return (draft.getCustomer() != null || draft.getCustomerRegistrationRequest() != null)
                && draft.wheelQuantity(WheelType.BIPARTITE) != null
                && draft.wheelQuantity(WheelType.WASHED) != null
                && draft.wheelQuantity(WheelType.NORMAL) != null
                && draft.totalWheelQuantity() > 0
                && draft.getFactoryDropoffDate() != null
                && draft.getFactoryDropoffSlot() != null
                && draft.getReadyDate() != null
                && draft.getFactoryPickupSlot() != null;
    }

    private LocalDate parseFutureDate(String text, Long chatId, String question) {
        LocalDate date = parseDate(text);
        if (date == null) {
            botClient.sendMessage(chatId, "A data é inválida. Use o formato DD/MM/AAAA.\n\n" + question);
            return null;
        }
        LocalDate today = LocalDate.now(clock.withZone(businessZone));
        if (date.isBefore(today)) {
            botClient.sendMessage(chatId, "A data não pode ser passada.\n\n" + question);
            return null;
        }
        return date;
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException | NullPointerException ex) {
            return null;
        }
    }

    private void askForCurrentState(TelegramConversationEntity conversation, Long chatId) {
        switch (conversation.getState()) {
            case AWAITING_CUSTOMER, AWAITING_CUSTOMER_NAME -> botClient.sendMessage(chatId, conversationFlow.customerQuestion());
            case AWAITING_CUSTOMER_SELECTION -> {
                List<ConversationCustomerCandidateEntity> options = customerResolution.currentOptions(conversation);
                botClient.sendMessage(chatId, customerOptionsText(options), customerOptionsKeyboard(options));
            }
            case AWAITING_NEW_CUSTOMER_CONFIRMATION -> botClient.sendMessage(chatId, "Responda com Confirmar nome, Corrigir nome ou Cancelar.");
            case AWAITING_NEW_CUSTOMER_DETAILS, AWAITING_NEW_CUSTOMER_FINAL_CONFIRMATION -> botClient.sendMessage(chatId, newCustomerFinalMessage(conversation));
            case AWAITING_BIPARTITE_QUANTITY -> botClient.sendMessage(chatId, conversationFlow.quantityQuestion(WheelType.BIPARTITE));
            case AWAITING_WASHED_QUANTITY -> botClient.sendMessage(chatId, conversationFlow.quantityQuestion(WheelType.WASHED));
            case AWAITING_NORMAL_QUANTITY -> botClient.sendMessage(chatId, conversationFlow.quantityQuestion(WheelType.NORMAL));
            case AWAITING_FACTORY_DROPOFF_DATE -> botClient.sendMessage(chatId, "5/10 — Em que data serão deixadas na fábrica? Responda no formato DD/MM/AAAA.");
            case AWAITING_FACTORY_DROPOFF_SLOT -> botClient.sendMessage(chatId, dropoffSlotQuestion());
            case AWAITING_READY_DATE -> botClient.sendMessage(chatId, "7/10 — Em que dia têm de estar prontas? Responda no formato DD/MM/AAAA.");
            case AWAITING_FACTORY_PICKUP_SLOT -> botClient.sendMessage(chatId, pickupSlotQuestion());
            case AWAITING_NOTES -> botClient.sendMessage(chatId, "9/10 — Alguma nota extra? Escreva a nota ou responda ‘Não’.");
            case AWAITING_CONFIRMATION -> sendSummary(chatId, requireDraft(conversation));
            default -> botClient.sendMessage(chatId, helpText());
        }
    }

    private void sendSummary(Long chatId, TelegramIntakeDraftEntity draft) {
        botClient.sendMessage(chatId, conversationFlow.summary(draft, DATE_FORMATTER), List.of(List.of(
                new TelegramButton("Confirmar pedido", "Confirmar pedido"),
                new TelegramButton("Corrigir", "Corrigir"),
                new TelegramButton("Cancelar", "Cancelar")
        )));
    }

    private void sendRecentRequests(DriverEntity driver, Long chatId) {
        List<WheelIntakeRequestEntity> recent = requests.findByDriverId(driver.getId(),
                        PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        if (recent.isEmpty()) {
            botClient.sendMessage(chatId, "Ainda não existem pedidos registados.");
            return;
        }
        String text = recent.stream()
                .map(request -> request.getCustomerNameSnapshot() + " — "
                        + request.getExpectedWheelQuantity() + " jantes — "
                        + request.getLifecycleStatus())
                .collect(Collectors.joining("\n"));
        botClient.sendMessage(chatId, "Pedidos recentes:\n" + text);
    }

    private String dropoffSlotQuestion() {
        return """
                6/10 — Entre que horas serão deixadas na fábrica?

                1 — Das 09:00 às 14:00
                2 — Das 14:00 às 19:00
                3 — Das 19:00 à madrugada

                Responda apenas com 1, 2 ou 3.""";
    }

    private String pickupSlotQuestion() {
        return """
                8/10 — Entre que horas têm de ser levantadas na fábrica?

                1 — Das 09:00 às 14:00
                2 — Das 14:00 às 19:00
                3 — Das 19:00 à madrugada

                Responda apenas com 1, 2 ou 3.""";
    }

    private String correctionQuestion() {
        return """
                Que campo pretende corrigir?

                1 — Cliente
                2 — Jantes bipartidas
                3 — Jantes lavadas
                4 — Jantes normais
                5 — Data de entrada na fábrica
                6 — Horário de entrada na fábrica
                7 — Data em que devem estar prontas
                8 — Horário de levantamento na fábrica
                9 — Notas""";
    }

    private String helpText() {
        return """
                Comandos disponíveis:
                /start — iniciar ou retomar o registo
                /novo — criar um novo pedido
                /cancelar — cancelar o pedido em curso
                /pedidos — ver pedidos recentes
                /perfil — consultar ou alterar o nome registado
                /ajuda — mostrar esta ajuda""";
    }

    private String normalizeNotes(String text) {
        String cleaned = normalizeSpaces(text)
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
        String normalized = normalizeCommandText(cleaned);
        if (normalized.equals("nao") || normalized.equals("não") || normalized.equals("nenhuma")
                || normalized.equals("nenhum") || normalized.equals("sem notas")) {
            return null;
        }
        if (cleaned.isBlank()) {
            return null;
        }
        return cleaned.length() > NOTES_LIMIT ? cleaned.substring(0, NOTES_LIMIT) : cleaned;
    }

    private String normalizeSpaces(String value) {
        return nullToBlank(value).trim().replaceAll("\\s+", " ");
    }

    private String normalizeCommandText(String value) {
        String normalized = Normalizer.normalize(normalizeSpaces(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return normalized;
    }

    private String command(String text) {
        String clean = normalizeSpaces(text);
        return clean.startsWith("/") ? clean.split("\\s+", 2)[0].toLowerCase(Locale.ROOT) : null;
    }

    private List<UUID> parseIds(String csv) {
        return Arrays.stream(csv.split(","))
                .filter(value -> !value.isBlank())
                .map(UUID::fromString)
                .toList();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizePhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }
        String normalized = rawPhone.trim().replaceAll("[\\s().-]", "");
        if (normalized.startsWith("00")) {
            normalized = "+" + normalized.substring(2);
        }
        if (!normalized.startsWith("+")) {
            normalized = "+" + normalized;
        }
        return normalized.matches("\\+[1-9][0-9]{6,14}") ? normalized : null;
    }

    private String safeError(RuntimeException ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
