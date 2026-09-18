package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.ConversationCustomerOptionType;
import pt.rucodel.productionplanning.entity.ConversationCustomerCandidateEntity;
import pt.rucodel.productionplanning.entity.CustomerReferenceEntity;
import pt.rucodel.productionplanning.entity.TelegramConversationEntity;
import pt.rucodel.productionplanning.repository.ConversationCustomerCandidateRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CustomerResolutionService {
    private final CustomerSearchService searchService;
    private final ConversationCustomerCandidateRepository candidates;
    private final Clock clock;

    public CustomerResolutionService(CustomerSearchService searchService,
                                     ConversationCustomerCandidateRepository candidates,
                                     Clock clock) {
        this.searchService = searchService;
        this.candidates = candidates;
        this.clock = clock;
    }

    public String normalize(String value) {
        return searchService.normalize(value);
    }

    public String displayInput(String value) {
        return searchService.displayInput(value);
    }

    @Transactional
    public CustomerResolutionResult resolveInitial(TelegramConversationEntity conversation, String rawName) {
        String original = searchService.displayInput(rawName);
        String normalized = searchService.normalize(original);
        if (normalized.isBlank()) {
            return CustomerResolutionResult.invalid("O cliente não pode ficar vazio.");
        }

        clearCandidates(conversation);
        List<CustomerReferenceEntity> exact = searchService.exactNormalized(original);
        if (exact.size() == 1) {
            return CustomerResolutionResult.exact(exact.getFirst());
        }

        List<CustomerSearchResult> suggestions = exact.isEmpty()
                ? searchService.suggest(original)
                : exact.stream().map(customer -> new CustomerSearchResult(customer, 1.0, distinction(customer))).toList();
        persistOptions(conversation, original, normalized, suggestions, exact.isEmpty());
        return CustomerResolutionResult.options(original, normalized, suggestions, exact.isEmpty());
    }

    @Transactional(readOnly = true)
    public List<ConversationCustomerCandidateEntity> currentOptions(TelegramConversationEntity conversation) {
        return candidates.findByConversationIdOrderByPositionAsc(conversation.getId());
    }

    @Transactional(readOnly = true)
    public Optional<ConversationCustomerCandidateEntity> option(TelegramConversationEntity conversation, int position) {
        return candidates.findByConversationIdAndPosition(conversation.getId(), position)
                .filter(option -> option.getExpiresAt().isAfter(OffsetDateTime.now(clock)));
    }

    @Transactional
    public void clearCandidates(TelegramConversationEntity conversation) {
        if (conversation.getId() != null) {
            candidates.deleteByConversationId(conversation.getId());
        }
    }

    private void persistOptions(TelegramConversationEntity conversation, String original, String normalized,
                                List<CustomerSearchResult> suggestions, boolean noExactMatch) {
        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plusHours(12);
        int position = 1;
        for (CustomerSearchResult suggestion : suggestions) {
            ConversationCustomerCandidateEntity option = baseOption(conversation, original, normalized, expiresAt, position++);
            option.setOptionType(ConversationCustomerOptionType.EXISTING_CUSTOMER);
            option.setCustomer(suggestion.customer());
            option.setCustomerNameSnapshot(suggestion.customer().getName());
            option.setSimilarityScore(suggestion.score());
            candidates.save(option);
        }
        ConversationCustomerCandidateEntity create = baseOption(conversation, original, normalized, expiresAt, position++);
        create.setOptionType(ConversationCustomerOptionType.CREATE_NEW_CUSTOMER);
        create.setCustomerNameSnapshot(original);
        create.setSimilarityScore(0.0);
        candidates.save(create);
        if (suggestions.isEmpty() && noExactMatch) {
            ConversationCustomerCandidateEntity correct = baseOption(conversation, original, normalized, expiresAt, position);
            correct.setOptionType(ConversationCustomerOptionType.CORRECT_NAME);
            correct.setCustomerNameSnapshot(original);
            correct.setSimilarityScore(0.0);
            candidates.save(correct);
        }
    }

    private ConversationCustomerCandidateEntity baseOption(TelegramConversationEntity conversation, String original,
                                                           String normalized, OffsetDateTime expiresAt, int position) {
        ConversationCustomerCandidateEntity option = new ConversationCustomerCandidateEntity();
        option.setConversation(conversation);
        option.setPosition(position);
        option.setOriginalSearchText(original);
        option.setNormalizedSearchText(normalized);
        option.setExpiresAt(expiresAt);
        option.setCreatedBy("TELEGRAM");
        option.setUpdatedBy("TELEGRAM");
        return option;
    }

    private String distinction(CustomerReferenceEntity customer) {
        return customer.getExternalId() == null || customer.getExternalId().isBlank()
                ? "sem número de cliente"
                : "n.º " + customer.getExternalId();
    }
}
