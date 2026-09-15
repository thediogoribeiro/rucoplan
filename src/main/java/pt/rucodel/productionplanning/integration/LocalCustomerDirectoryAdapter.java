package pt.rucodel.productionplanning.integration;

import org.springframework.stereotype.Component;
import pt.rucodel.productionplanning.repository.CustomerReferenceRepository;

import java.util.List;
import java.util.Optional;

@Component
public class LocalCustomerDirectoryAdapter implements CustomerDirectoryPort {
    private final CustomerReferenceRepository customers;

    public LocalCustomerDirectoryAdapter(CustomerReferenceRepository customers) {
        this.customers = customers;
    }

    @Override
    public List<CustomerDirectoryCustomer> searchCustomers(String query, int limit) {
        String effectiveQuery = query == null ? "" : query.trim();
        List<CustomerDirectoryCustomer> result = (effectiveQuery.isBlank()
                ? customers.findByActiveTrueOrderByName()
                : customers.search(effectiveQuery))
                .stream()
                .limit(Math.max(1, Math.min(limit, 50)))
                .map(customer -> new CustomerDirectoryCustomer(customer.getId(), customer.getExternalId(), customer.getName()))
                .toList();
        return result;
    }

    @Override
    public Optional<CustomerDirectoryCustomer> resolveByExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        return customers.findByExternalId(externalId.trim())
                .map(customer -> new CustomerDirectoryCustomer(customer.getId(), customer.getExternalId(), customer.getName()));
    }

    @Override
    public List<CustomerDirectoryCustomer> resolveByExactName(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        return customers.findByNameIgnoreCase(name.trim()).stream()
                .filter(customer -> customer.isActive())
                .map(customer -> new CustomerDirectoryCustomer(customer.getId(), customer.getExternalId(), customer.getName()))
                .toList();
    }
}
