package pt.rucodel.productionplanning.whatsapp;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.*;
import pt.rucodel.productionplanning.dto.CustomerRequest;
import pt.rucodel.productionplanning.entity.*;
import pt.rucodel.productionplanning.exception.InvalidRequestException;
import pt.rucodel.productionplanning.repository.CustomerReferenceRepository;
import pt.rucodel.productionplanning.repository.WhatsAppConversationRepository;
import pt.rucodel.productionplanning.repository.WhatsAppIngestionItemRepository;
import pt.rucodel.productionplanning.service.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;

@Service
public class WhatsAppUpdateProcessor {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    private final WhatsAppInboundMessageMapper mapper;
    private final WhatsAppIngestionItemRepository inboundMessages;
    private final WhatsAppConversationRepository conversations;
    private final MessagingIdentityService identities;
    private final DriverRegistrationService driverRegistration;
    private final CustomerSearchService customerSearch;
    private final CustomerService customerService;
    private final CustomerReferenceRepository customers;
    private final DriverIntakeConversationService conversationText;
    private final WheelIntakeRequestService intakeRequests;
    private final ProductionSiteService productionSites;
    private final WhatsAppCloudApiClient outbound;
    private final Clock clock;
    private final ZoneId businessZone;
    private final LocalTime overnightEndTime;

    public WhatsAppUpdateProcessor(WhatsAppInboundMessageMapper mapper,
                                   WhatsAppIngestionItemRepository inboundMessages,
                                   WhatsAppConversationRepository conversations,
                                   MessagingIdentityService identities,
                                   DriverRegistrationService driverRegistration,
                                   CustomerSearchService customerSearch,
                                   CustomerService customerService,
                                   CustomerReferenceRepository customers,
                                   DriverIntakeConversationService conversationText,
                                   WheelIntakeRequestService intakeRequests,
                                   ProductionSiteService productionSites,
                                   WhatsAppCloudApiClient outbound,
                                   Clock clock,
                                   AppProperties appProperties,
                                   @org.springframework.beans.factory.annotation.Value("${app.planning.overnight-end-time:06:00}") String overnightEndTime) {
        this.mapper = mapper;
        this.inboundMessages = inboundMessages;
        this.conversations = conversations;
        this.identities = identities;
        this.driverRegistration = driverRegistration;
        this.customerSearch = customerSearch;
        this.customerService = customerService;
        this.customers = customers;
        this.conversationText = conversationText;
        this.intakeRequests = intakeRequests;
        this.productionSites = productionSites;
        this.outbound = outbound;
        this.clock = clock;
        this.businessZone = ZoneId.of(appProperties.timezone());
        this.overnightEndTime = LocalTime.parse(overnightEndTime);
    }

    @Transactional
    public void process(String rawPayload) {
        for (ConversationMessage message : mapper.map(rawPayload)) {
            processMessage(message, rawPayload);
        }
    }

    private void processMessage(ConversationMessage message, String rawPayload) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<WhatsAppIngestionItemEntity> existing = inboundMessages.findByExternalMessageId(message.externalMessageId());
        if (existing.isPresent() && existing.get().getProcessedAt() != null) {
            return;
        }
        WhatsAppIngestionItemEntity inbound = existing.orElseGet(() -> createInbound(message, rawPayload, now));
        try {
            if (!"text".equals(message.messageType()) && !"button".equals(message.messageType()) && !"interactive".equals(message.messageType())) {
                inbound.setStatus(IngestionStatus.IGNORED);
                inbound.setReason("Unsupported WhatsApp message type.");
                inbound.setProcessedAt(now);
                inboundMessages.save(inbound);
                return;
            }
            String response = handleConversation(message);
            inbound.setStatus(IngestionStatus.CREATED);
            inbound.setProcessedAt(now);
            inbound.setReason("Message processed.");
            String outboundId = outbound.sendText(message.fromWaId(), response);
            inbound.setOutboundMessageId(outboundId);
            inboundMessages.save(inbound);
        } catch (RuntimeException ex) {
            inbound.setStatus(IngestionStatus.FAILED);
            inbound.setRetryCount(inbound.getRetryCount() + 1);
            inbound.setLastError(safeError(ex));
            inboundMessages.save(inbound);
            throw ex;
        }
    }

    private WhatsAppIngestionItemEntity createInbound(ConversationMessage message, String rawPayload, OffsetDateTime now) {
        WhatsAppIngestionItemEntity item = new WhatsAppIngestionItemEntity();
        item.setExternalMessageId(message.externalMessageId());
        item.setWhatsappBusinessAccountId(message.whatsappBusinessAccountId());
        item.setPhoneNumberId(message.phoneNumberId());
        item.setSenderWaId(message.fromWaId());
        item.setMessageType(message.messageType());
        item.setReceivedAt(message.receivedAt() == null ? now : message.receivedAt());
        item.setPayloadHash(hash(rawPayload));
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        item.setStatus(IngestionStatus.NEEDS_REVIEW);
        return inboundMessages.saveAndFlush(item);
    }

    private String handleConversation(ConversationMessage message) {
        MessagingIdentityEntity identity = identities.resolveWhatsAppIdentity(new WhatsAppIdentitySnapshot(
                message.fromWaId(),
                message.phoneNumber(),
                message.profileName(),
                message.phoneNumberId()
        ));
        WhatsAppConversationEntity conversation = conversations.findWithLockByMessagingIdentityId(identity.getId())
                .orElseGet(() -> newConversation(identity));
        String text = normalize(message.text());
        if (identity.getDriver() == null) {
            DriverEntity driver = driverRegistration.registerFromWhatsAppName(identity, text);
            conversation.setState(WhatsAppConversationState.AWAITING_PRODUCTION_SITE);
            conversation.setUpdatedBy("WHATSAPP");
            conversations.save(conversation);
            return "Obrigado, %s.\n\n%s".formatted(driver.getName(), productionSiteQuestion());
        }
        if ("/novo".equalsIgnoreCase(text) || conversation.getState() == WhatsAppConversationState.IDLE) {
            resetConversation(conversation);
            conversation.setState(WhatsAppConversationState.AWAITING_PRODUCTION_SITE);
            conversations.save(conversation);
            return productionSiteQuestion();
        }
        if ("/cancelar".equalsIgnoreCase(text)) {
            resetConversation(conversation);
            conversation.setState(WhatsAppConversationState.IDLE);
            conversations.save(conversation);
            return "Pedido cancelado. Envie /novo para começar outro pedido.";
        }
        return advance(conversation, identity.getDriver(), text);
    }

    private String advance(WhatsAppConversationEntity conversation, DriverEntity driver, String text) {
        return switch (conversation.getState()) {
            case AWAITING_PRODUCTION_SITE -> handleProductionSite(conversation, driver, text);
            case AWAITING_CUSTOMER_NAME -> handleCustomerName(conversation, text);
            case AWAITING_CUSTOMER_OPTION -> handleCustomerOption(conversation, text);
            case AWAITING_NEW_CUSTOMER_TAX_IDENTIFIER -> {
                if (text.isBlank()) {
                    yield "O NIF/VAT não pode ficar vazio.";
                }
                conversation.setNewCustomerTaxIdentifier(text.toUpperCase(Locale.ROOT));
                conversation.setState(WhatsAppConversationState.AWAITING_NEW_CUSTOMER_COUNTRY);
                yield "Indique o país por código ISO, por exemplo PT, ES, FR ou LU.";
            }
            case AWAITING_NEW_CUSTOMER_COUNTRY -> {
                String country = text.toUpperCase(Locale.ROOT);
                if (!country.matches("[A-Z]{2}")) {
                    yield "Indique o país com código ISO de duas letras, por exemplo PT.";
                }
                conversation.setNewCustomerCountryCode(country);
                conversation.setState(WhatsAppConversationState.AWAITING_NEW_CUSTOMER_LOCALITY);
                yield "Qual é a localidade do cliente?";
            }
            case AWAITING_NEW_CUSTOMER_LOCALITY -> {
                if (text.isBlank()) {
                    yield "A localidade não pode ficar vazia.";
                }
                conversation.setNewCustomerLocality(text);
                conversation.setState(WhatsAppConversationState.AWAITING_NEW_CUSTOMER_CONFIRMATION);
                yield newCustomerSummary(conversation);
            }
            case AWAITING_NEW_CUSTOMER_CONFIRMATION -> handleNewCustomerConfirmation(conversation, text);
            case AWAITING_BIPARTITE_QUANTITY -> handleQuantity(conversation, text, WheelType.BIPARTITE, WhatsAppConversationState.AWAITING_WASHED_QUANTITY);
            case AWAITING_WASHED_QUANTITY -> handleQuantity(conversation, text, WheelType.WASHED, WhatsAppConversationState.AWAITING_NORMAL_QUANTITY);
            case AWAITING_NORMAL_QUANTITY -> handleNormalQuantity(conversation, text);
            case AWAITING_FACTORY_DROPOFF_DATE -> handleDropoffDate(conversation, text);
            case AWAITING_FACTORY_DROPOFF_SLOT -> handleDropoffSlot(conversation, text);
            case AWAITING_READY_DATE -> handleReadyDate(conversation, text);
            case AWAITING_FACTORY_PICKUP_SLOT -> handlePickupSlot(conversation, text);
            case AWAITING_NOTES -> {
                conversation.setNotes("sem notas".equalsIgnoreCase(text) ? null : text);
                conversation.setState(WhatsAppConversationState.AWAITING_CONFIRMATION);
                yield summary(conversation);
            }
            case AWAITING_CONFIRMATION -> handleConfirmation(conversation, driver, text);
            default -> {
                conversation.setState(WhatsAppConversationState.AWAITING_PRODUCTION_SITE);
                yield productionSiteQuestion();
            }
        };
    }

    private String handleProductionSite(WhatsAppConversationEntity conversation, DriverEntity driver, String text) {
        ProductionSiteCode siteCode = parseProductionSite(text);
        if (siteCode == null) {
            return "Escolha 1 para Portugal ou 2 para Luxemburgo.\n\n" + productionSiteQuestion();
        }
        ProductionSiteEntity site = productionSites.requireActive(siteCode);
        conversation.setProductionSite(site);
        conversation.setCustomer(null);
        conversation.setCandidateCustomerIds(null);
        conversation.setNewCustomerName(null);
        conversation.setState(WhatsAppConversationState.AWAITING_CUSTOMER_NAME);
        conversation.setUpdatedBy("WHATSAPP");
        productionSites.ensureDriverAssociation(driver, site, DriverProductionSiteAssociationSource.WHATSAPP_SELECTION, "WHATSAPP");
        return "Unidade de produção: %s.\n\n%s".formatted(site.getDisplayName(), conversationText.customerQuestion());
    }

    private String handleCustomerName(WhatsAppConversationEntity conversation, String text) {
        if (text.isBlank()) {
            return "O cliente não pode ficar vazio.";
        }
        if (conversation.getProductionSite() == null) {
            conversation.setState(WhatsAppConversationState.AWAITING_PRODUCTION_SITE);
            return productionSiteQuestion();
        }
        ProductionSiteCode siteCode = siteCode(conversation);
        List<CustomerReferenceEntity> exact = customerSearch.exactNormalized(siteCode, text);
        if (exact.size() == 1) {
            conversation.setCustomer(exact.getFirst());
            conversation.setState(WhatsAppConversationState.AWAITING_BIPARTITE_QUANTITY);
            return "Cliente identificado: %s.\n\n%s".formatted(exact.getFirst().getName(),
                    conversationText.quantityQuestion(WheelType.BIPARTITE));
        }
        List<CustomerSearchResult> suggestions = customerSearch.suggest(siteCode, text);
        conversation.setNewCustomerName(text);
        conversation.setCandidateCustomerIds(suggestionIds(suggestions));
        conversation.setState(WhatsAppConversationState.AWAITING_CUSTOMER_OPTION);
        return customerOptionsMessage(suggestions);
    }

    private String handleCustomerOption(WhatsAppConversationEntity conversation, String text) {
        int option = option(text);
        List<UUID> ids = candidateIds(conversation);
        if (option >= 1 && option <= ids.size()) {
            CustomerReferenceEntity customer = customers.findByIdForSite(siteCode(conversation), ids.get(option - 1))
                    .orElseThrow(() -> new InvalidRequestException("Customer option no longer exists."));
            conversation.setCustomer(customer);
            conversation.setState(WhatsAppConversationState.AWAITING_BIPARTITE_QUANTITY);
            return "Cliente selecionado: %s.\n\n%s".formatted(customer.getName(), conversationText.quantityQuestion(WheelType.BIPARTITE));
        }
        if (option == ids.size() + 1) {
            conversation.setState(WhatsAppConversationState.AWAITING_NEW_CUSTOMER_TAX_IDENTIFIER);
            return "Vamos criar o cliente \"%s\".\n\nQual é o NIF/VAT?".formatted(conversation.getNewCustomerName());
        }
        return "Responda com o número de uma das opções.";
    }

    private String handleNewCustomerConfirmation(WhatsAppConversationEntity conversation, String text) {
        String clean = text.toLowerCase(Locale.ROOT);
        if (!Set.of("sim", "confirmar", "ok", "s").contains(clean)) {
            return "Responda Sim para criar o cliente ou /cancelar para cancelar.";
        }
        CustomerRequest request = new CustomerRequest(
                null,
                null,
                null,
                conversation.getNewCustomerName(),
                conversation.getNewCustomerTaxIdentifier(),
                conversation.getNewCustomerCountryCode(),
                countryName(conversation.getNewCustomerCountryCode()),
                conversation.getNewCustomerLocality(),
                true,
                null
        );
        ProductionSiteCode siteCode = siteCode(conversation);
        UUID customerId = customerService.create(siteCode, request, "WHATSAPP").id();
        CustomerReferenceEntity customer = customers.findByIdForSite(siteCode, customerId)
                .orElseThrow(() -> new InvalidRequestException("Customer was not created."));
        conversation.setCustomer(customer);
        conversation.setState(WhatsAppConversationState.AWAITING_BIPARTITE_QUANTITY);
        return "Cliente criado com o código %s.\n\n%s".formatted(customer.getCustomerCode(),
                conversationText.quantityQuestion(WheelType.BIPARTITE));
    }

    private String handleQuantity(WhatsAppConversationEntity conversation, String text, WheelType type, WhatsAppConversationState next) {
        Integer quantity = conversationText.parseQuantity(text);
        if (quantity == null) {
            return "Indique um número inteiro maior ou igual a zero.\n\n" + conversationText.quantityQuestion(type);
        }
        conversation.setWheelQuantity(type, quantity);
        conversation.setState(next);
        WheelType nextType = type == WheelType.BIPARTITE ? WheelType.WASHED : WheelType.NORMAL;
        return conversationText.quantityQuestion(nextType);
    }

    private String handleNormalQuantity(WhatsAppConversationEntity conversation, String text) {
        Integer quantity = conversationText.parseQuantity(text);
        if (quantity == null) {
            return "Indique um número inteiro maior ou igual a zero.\n\n" + conversationText.quantityQuestion(WheelType.NORMAL);
        }
        conversation.setWheelQuantity(WheelType.NORMAL, quantity);
        if (conversation.totalWheelQuantity() <= 0) {
            conversation.setState(WhatsAppConversationState.AWAITING_BIPARTITE_QUANTITY);
            return "O pedido tem de ter pelo menos uma jante.\n\n" + conversationText.quantityQuestion(WheelType.BIPARTITE);
        }
        conversation.setState(WhatsAppConversationState.AWAITING_FACTORY_DROPOFF_DATE);
        return "5/10 — Em que data prevê deixar as jantes na fábrica? Responda no formato dd/MM/aaaa.";
    }

    private String handleDropoffDate(WhatsAppConversationEntity conversation, String text) {
        LocalDate date = parseDate(text);
        if (date == null) {
            return "Data inválida. Use o formato dd/MM/aaaa.";
        }
        conversation.setFactoryDropoffDate(date);
        conversation.setState(WhatsAppConversationState.AWAITING_FACTORY_DROPOFF_SLOT);
        return slotQuestion("6/10 — Qual é o intervalo de chegada?");
    }

    private String handleDropoffSlot(WhatsAppConversationEntity conversation, String text) {
        FactoryTimeSlot slot = FactoryTimeSlot.fromOption(text);
        if (slot == null) {
            return slotQuestion("Escolha uma opção válida para a chegada.");
        }
        conversation.setFactoryDropoffSlot(slot);
        conversation.setState(WhatsAppConversationState.AWAITING_READY_DATE);
        return "7/10 — Para que data pretende o levantamento? Responda no formato dd/MM/aaaa.";
    }

    private String handleReadyDate(WhatsAppConversationEntity conversation, String text) {
        LocalDate date = parseDate(text);
        if (date == null) {
            return "Data inválida. Use o formato dd/MM/aaaa.";
        }
        conversation.setReadyDate(date);
        conversation.setState(WhatsAppConversationState.AWAITING_FACTORY_PICKUP_SLOT);
        return slotQuestion("8/10 — Qual é o intervalo de levantamento?");
    }

    private String handlePickupSlot(WhatsAppConversationEntity conversation, String text) {
        FactoryTimeSlot slot = FactoryTimeSlot.fromOption(text);
        if (slot == null) {
            return slotQuestion("Escolha uma opção válida para o levantamento.");
        }
        conversation.setFactoryPickupSlot(slot);
        conversation.setState(WhatsAppConversationState.AWAITING_NOTES);
        return "9/10 — Quer acrescentar notas? Responda com o texto ou \"sem notas\".";
    }

    private String handleConfirmation(WhatsAppConversationEntity conversation, DriverEntity driver, String text) {
        String clean = text.toLowerCase(Locale.ROOT);
        if (!Set.of("sim", "confirmar", "confirmar pedido", "ok", "s").contains(clean)) {
            return "Responda Confirmar para criar o pedido, /cancelar para cancelar ou /novo para reiniciar.";
        }
        if (conversation.getConfirmedRequest() != null) {
            return "Este pedido já foi confirmado.";
        }
        if (conversation.getProductionSite() == null) {
            conversation.setState(WhatsAppConversationState.AWAITING_PRODUCTION_SITE);
            return productionSiteQuestion();
        }
        ZoneId siteZone = zoneFor(conversation);
        OffsetDateTime dropoffStart = conversation.getFactoryDropoffSlot().startAt(conversation.getFactoryDropoffDate(), siteZone);
        OffsetDateTime dropoffEnd = conversation.getFactoryDropoffSlot().endAt(conversation.getFactoryDropoffDate(), siteZone, overnightEndTime);
        OffsetDateTime pickupStart = conversation.getFactoryPickupSlot().startAt(conversation.getReadyDate(), siteZone);
        OffsetDateTime pickupEnd = conversation.getFactoryPickupSlot().endAt(conversation.getReadyDate(), siteZone, overnightEndTime);
        WheelIntakeRequestEntity request = intakeRequests.createFromWhatsApp(
                "whatsapp-conversation-" + conversation.getId(),
                driver,
                conversation.getMessagingIdentity(),
                conversation.getCustomer(),
                conversation.wheelQuantityMap(),
                dropoffStart,
                dropoffEnd,
                conversation.getFactoryDropoffSlot(),
                pickupStart,
                pickupEnd,
                conversation.getFactoryPickupSlot(),
                conversation.getNotes()
        );
        conversation.setConfirmedRequest(request);
        conversation.setState(WhatsAppConversationState.IDLE);
        return "Pedido comunicado com sucesso.\n\nO pedido ficará pendente de confirmação de entrada na fábrica quando as jantes forem entregues.";
    }

    private WhatsAppConversationEntity newConversation(MessagingIdentityEntity identity) {
        WhatsAppConversationEntity conversation = new WhatsAppConversationEntity();
        conversation.setMessagingIdentity(identity);
        conversation.setCreatedBy("WHATSAPP");
        conversation.setUpdatedBy("WHATSAPP");
        conversation.setState(identity.getDriver() == null
                ? WhatsAppConversationState.AWAITING_DRIVER_NAME
                : WhatsAppConversationState.AWAITING_PRODUCTION_SITE);
        return conversations.saveAndFlush(conversation);
    }

    private void resetConversation(WhatsAppConversationEntity conversation) {
        conversation.setProductionSite(null);
        conversation.setCustomer(null);
        conversation.setNewCustomerName(null);
        conversation.setNewCustomerTaxIdentifier(null);
        conversation.setNewCustomerCountryCode(null);
        conversation.setNewCustomerLocality(null);
        conversation.setCandidateCustomerIds(null);
        for (WheelType type : WheelType.values()) {
            conversation.setWheelQuantity(type, null);
        }
        conversation.setFactoryDropoffDate(null);
        conversation.setFactoryDropoffSlot(null);
        conversation.setReadyDate(null);
        conversation.setFactoryPickupSlot(null);
        conversation.setNotes(null);
        conversation.setConfirmedRequest(null);
        conversation.setUpdatedBy("WHATSAPP");
    }

    private String customerOptionsMessage(List<CustomerSearchResult> suggestions) {
        StringBuilder builder = new StringBuilder("Encontrámos clientes com nomes semelhantes:\n\n");
        int position = 1;
        for (CustomerSearchResult suggestion : suggestions) {
            builder.append(position++).append(". ").append(suggestion.customer().getName()).append('\n');
        }
        builder.append(position).append(". Criar novo cliente\n\nResponda com o número da opção.");
        return builder.toString();
    }

    private String newCustomerSummary(WhatsAppConversationEntity conversation) {
        return """
                Confirma a criação deste cliente?

                Nome: %s
                NIF/VAT: %s
                País: %s
                Localidade: %s

                Responda Sim para confirmar.
                """.formatted(
                conversation.getNewCustomerName(),
                conversation.getNewCustomerTaxIdentifier(),
                conversation.getNewCustomerCountryCode(),
                conversation.getNewCustomerLocality()
        );
    }

    private String summary(WhatsAppConversationEntity conversation) {
        return """
                10/10 — Confirma o pedido?

                Unidade de produção: %s
                Cliente: %s

                Tipos de jantes:
                * Bipartidas: %d
                * Lavadas: %d
                * Normais: %d
                * Total: %d jantes

                Entrada prevista na fábrica: %s, %s
                Devem estar prontas: %s, %s
                Notas: %s

                Responda Confirmar para criar o pedido.
                """.formatted(
                conversation.getProductionSite() == null ? "Portugal" : conversation.getProductionSite().getDisplayName(),
                conversation.getCustomer().getName(),
                conversation.wheelQuantity(WheelType.BIPARTITE),
                conversation.wheelQuantity(WheelType.WASHED),
                conversation.wheelQuantity(WheelType.NORMAL),
                conversation.totalWheelQuantity(),
                DATE_FORMATTER.format(conversation.getFactoryDropoffDate()),
                conversation.getFactoryDropoffSlot().label(),
                DATE_FORMATTER.format(conversation.getReadyDate()),
                conversation.getFactoryPickupSlot().label(),
                conversation.getNotes() == null ? "Sem notas" : conversation.getNotes()
        );
    }

    private String slotQuestion(String title) {
        return title + "\n\n1 — 09:00 às 14:00\n2 — 14:00 às 19:00\n3 — 19:00 à madrugada";
    }

    private String productionSiteQuestion() {
        return """
                Para qual unidade de produção é este pedido?

                1 — Portugal
                2 — Luxemburgo""";
    }

    private ProductionSiteCode parseProductionSite(String text) {
        String normalized = normalize(text).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "1", "pt", "portugal" -> ProductionSiteCode.PT;
            case "2", "lux", "luxemburgo", "luxembourg" -> ProductionSiteCode.LUX;
            default -> null;
        };
    }

    private ProductionSiteCode siteCode(WhatsAppConversationEntity conversation) {
        if (conversation.getProductionSite() == null || conversation.getProductionSite().getCode() == null) {
            return ProductionSiteCode.PT;
        }
        return conversation.getProductionSite().getCode();
    }

    private ZoneId zoneFor(WhatsAppConversationEntity conversation) {
        return conversation.getProductionSite() == null || conversation.getProductionSite().getTimezone() == null
                ? businessZone
                : ZoneId.of(conversation.getProductionSite().getTimezone());
    }

    private int option(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String countryName(String countryCode) {
        return switch (countryCode == null ? "" : countryCode) {
            case "PT" -> "Portugal";
            case "LU" -> "Luxemburgo";
            case "FR" -> "França";
            case "ES" -> "Espanha";
            default -> countryCode;
        };
    }

    private String suggestionIds(List<CustomerSearchResult> suggestions) {
        return suggestions.stream()
                .map(suggestion -> suggestion.customer().getId().toString())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private List<UUID> candidateIds(WhatsAppConversationEntity conversation) {
        if (conversation.getCandidateCustomerIds() == null || conversation.getCandidateCustomerIds().isBlank()) {
            return List.of();
        }
        return Arrays.stream(conversation.getCandidateCustomerIds().split(","))
                .filter(value -> !value.isBlank())
                .map(UUID::fromString)
                .toList();
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String hash(String rawPayload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawPayload.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            return null;
        }
    }

    private String safeError(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
