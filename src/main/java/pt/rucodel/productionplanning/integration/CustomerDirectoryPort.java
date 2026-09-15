package pt.rucodel.productionplanning.integration;

import java.util.List;
import java.util.Optional;

public interface CustomerDirectoryPort {
    List<CustomerDirectoryCustomer> searchCustomers(String query, int limit);

    Optional<CustomerDirectoryCustomer> resolveByExternalId(String externalId);

    List<CustomerDirectoryCustomer> resolveByExactName(String name);
}
