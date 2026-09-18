package pt.rucodel.productionplanning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.entity.CustomerReferenceEntity;
import pt.rucodel.productionplanning.repository.CustomerReferenceRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CustomerSearchService {
    private final CustomerReferenceRepository customers;
    private final CustomerNameNormalizer normalizer;
    private final int suggestionLimit;
    private final double threshold;

    public CustomerSearchService(CustomerReferenceRepository customers,
                                 CustomerNameNormalizer normalizer,
                                 @Value("${app.customers.search.suggestion-limit:5}") int suggestionLimit,
                                 @Value("${app.customers.search.similarity-threshold:0.30}") double threshold) {
        this.customers = customers;
        this.normalizer = normalizer;
        this.suggestionLimit = Math.max(1, Math.min(suggestionLimit, 10));
        this.threshold = Math.max(0.0, Math.min(threshold, 1.0));
    }

    public String normalize(String value) {
        return normalizer.normalize(value);
    }

    public String displayInput(String value) {
        return normalizer.displayInput(value);
    }

    public int suggestionLimit() {
        return suggestionLimit;
    }

    public double threshold() {
        return threshold;
    }

    @Transactional(readOnly = true)
    public List<CustomerReferenceEntity> exactNormalized(String name) {
        String normalized = normalizer.normalize(name);
        if (normalized.isBlank()) {
            return List.of();
        }
        return customers.findByActiveTrueAndNormalizedNameOrderByNameAscIdAsc(normalized);
    }

    @Transactional(readOnly = true)
    public List<CustomerSearchResult> suggest(String name) {
        String normalizedQuery = normalizer.normalize(name);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        return customers.findByActiveTrueOrderByName().stream()
                .map(customer -> new CustomerSearchResult(customer,
                        score(normalizedQuery, ensureNormalized(customer)),
                        distinction(customer)))
                .filter(result -> result.score() >= threshold)
                .sorted(Comparator
                        .comparingDouble(CustomerSearchResult::score).reversed()
                        .thenComparing(result -> result.customer().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(result -> result.customer().getId()))
                .limit(suggestionLimit)
                .toList();
    }

    public double score(String normalizedQuery, String normalizedCandidate) {
        if (normalizedQuery.equals(normalizedCandidate)) {
            return 1.0;
        }
        Set<String> queryTokens = tokens(normalizedQuery);
        Set<String> candidateTokens = tokens(normalizedCandidate);
        double tokenOverlap = jaccard(queryTokens, candidateTokens);
        double trigram = jaccard(trigrams(normalizedQuery), trigrams(normalizedCandidate));
        double containment = normalizedCandidate.contains(normalizedQuery) || normalizedQuery.contains(normalizedCandidate) ? 0.85 : 0.0;
        double prefix = prefixScore(queryTokens, candidateTokens);
        return Math.max(containment, Math.max(prefix, (trigram * 0.65) + (tokenOverlap * 0.35)));
    }

    private String ensureNormalized(CustomerReferenceEntity customer) {
        if (customer.getNormalizedName() != null && !customer.getNormalizedName().isBlank()) {
            return customer.getNormalizedName();
        }
        return normalizer.normalize(customer.getName());
    }

    private String distinction(CustomerReferenceEntity customer) {
        return customer.getExternalId() == null || customer.getExternalId().isBlank()
                ? "sem número de cliente"
                : "n.º " + customer.getExternalId();
    }

    private Set<String> tokens(String value) {
        return Arrays.stream(value.split("\\s+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> trigrams(String value) {
        String padded = "  " + value + "  ";
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i <= padded.length() - 3; i++) {
            result.add(padded.substring(i, i + 3));
        }
        return result;
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }

    private double prefixScore(Set<String> queryTokens, Set<String> candidateTokens) {
        if (queryTokens.isEmpty() || candidateTokens.isEmpty()) {
            return 0.0;
        }
        int matches = 0;
        for (String query : queryTokens) {
            for (String candidate : candidateTokens) {
                if (candidate.startsWith(query) || query.startsWith(candidate)) {
                    matches++;
                    break;
                }
            }
        }
        return Math.min(0.82, (double) matches / Math.max(queryTokens.size(), candidateTokens.size()));
    }
}
