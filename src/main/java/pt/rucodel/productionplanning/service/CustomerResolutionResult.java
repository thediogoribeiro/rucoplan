package pt.rucodel.productionplanning.service;

import pt.rucodel.productionplanning.entity.CustomerReferenceEntity;

import java.util.List;

public record CustomerResolutionResult(
        CustomerResolutionResultType type,
        CustomerReferenceEntity exactCustomer,
        String originalInput,
        String normalizedInput,
        List<CustomerSearchResult> suggestions,
        String error,
        boolean noExactMatch
) {
    public static CustomerResolutionResult invalid(String error) {
        return new CustomerResolutionResult(CustomerResolutionResultType.INVALID, null, null, null, List.of(), error, false);
    }

    public static CustomerResolutionResult exact(CustomerReferenceEntity customer) {
        return new CustomerResolutionResult(CustomerResolutionResultType.EXACT, customer, null, null, List.of(), null, false);
    }

    public static CustomerResolutionResult options(String originalInput, String normalizedInput,
                                                   List<CustomerSearchResult> suggestions, boolean noExactMatch) {
        return new CustomerResolutionResult(CustomerResolutionResultType.OPTIONS, null, originalInput, normalizedInput,
                suggestions, null, noExactMatch);
    }
}
