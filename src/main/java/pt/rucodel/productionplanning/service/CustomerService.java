package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.dto.CustomerRequest;
import pt.rucodel.productionplanning.dto.CustomerResponse;
import pt.rucodel.productionplanning.entity.CustomerReferenceEntity;
import pt.rucodel.productionplanning.exception.EntityNotFoundException;
import pt.rucodel.productionplanning.exception.InvalidRequestException;
import pt.rucodel.productionplanning.integration.CustomerDirectoryPort;
import pt.rucodel.productionplanning.mapper.ApiMapper;
import pt.rucodel.productionplanning.repository.CustomerReferenceRepository;

import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {
    private final CustomerReferenceRepository customers;
    private final CustomerDirectoryPort customerDirectory;
    private final ApiMapper mapper;

    public CustomerService(CustomerReferenceRepository customers, CustomerDirectoryPort customerDirectory, ApiMapper mapper) {
        this.customers = customers;
        this.customerDirectory = customerDirectory;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> search(String query, int limit) {
        return customerDirectory.searchCustomers(query, limit).stream()
                .map(customer -> new CustomerResponse(customer.localId(), customer.externalId(), customer.officialName(), true, 0))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> list() {
        return customers.findByActiveTrueOrderByName().stream().map(mapper::toCustomer).toList();
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request, String actor) {
        CustomerReferenceEntity entity = new CustomerReferenceEntity();
        apply(entity, request);
        entity.setCreatedBy(actor);
        entity.setUpdatedBy(actor);
        return mapper.toCustomer(customers.save(entity));
    }

    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request, String actor) {
        CustomerReferenceEntity entity = customers.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer was not found."));
        if (request.version() != null && request.version() != entity.getVersion()) {
            throw new InvalidRequestException("OPTIMISTIC_LOCK", "Customer was changed by another user.");
        }
        apply(entity, request);
        entity.setUpdatedBy(actor);
        return mapper.toCustomer(entity);
    }

    public CustomerReferenceEntity requireEntity(UUID id) {
        return customers.findById(id).filter(CustomerReferenceEntity::isActive)
                .orElseThrow(() -> new EntityNotFoundException("Customer was not found."));
    }

    private void apply(CustomerReferenceEntity entity, CustomerRequest request) {
        entity.setExternalId(blankToNull(request.externalId()));
        entity.setName(request.name().trim());
        entity.setActive(request.active() == null || request.active());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
