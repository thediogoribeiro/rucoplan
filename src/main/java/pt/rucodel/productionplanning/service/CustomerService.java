package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.dto.CustomerRequest;
import pt.rucodel.productionplanning.dto.CustomerResponse;
import pt.rucodel.productionplanning.domain.CustomerStatus;
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
    private final CustomerNameNormalizer normalizer;
    private final PublicCodeService publicCodes;

    public CustomerService(CustomerReferenceRepository customers, CustomerDirectoryPort customerDirectory, ApiMapper mapper,
                           CustomerNameNormalizer normalizer, PublicCodeService publicCodes) {
        this.customers = customers;
        this.customerDirectory = customerDirectory;
        this.mapper = mapper;
        this.normalizer = normalizer;
        this.publicCodes = publicCodes;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> search(String query, int limit) {
        return customerDirectory.searchCustomers(query, limit).stream()
                .map(customer -> customers.findById(customer.localId())
                        .map(mapper::toCustomer)
                        .orElseGet(() -> new CustomerResponse(customer.localId(), null, null, customer.externalId(),
                                null, null, customer.officialName(), null, null, null, null, true, 0)))
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
        if (entity.getCustomerNumber() == null) {
            entity.setCustomerNumber(customers.nextCustomerNumber());
        }
        if (entity.getCustomerCode() == null) {
            entity.setCustomerCode(publicCodes.customerCode(customers.nextCustomerCodeNumber()));
        }
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
        entity.setExternalSystem(blankToNull(request.externalSystem()));
        entity.setExternalCustomerId(blankToNull(request.externalCustomerId()));
        if (entity.getExternalSystem() != null && entity.getExternalCustomerId() != null) {
            customers.findByExternalSystemAndExternalCustomerId(entity.getExternalSystem(), entity.getExternalCustomerId())
                    .filter(existing -> !existing.getId().equals(entity.getId()))
                    .ifPresent(existing -> {
                        throw new InvalidRequestException("External customer reference is already linked to another customer.");
                    });
        }
        entity.setName(request.name().trim());
        entity.setNormalizedName(normalizer.normalize(request.name()));
        entity.setTaxIdentifier(normalizeTaxIdentifier(request.taxIdentifier()));
        entity.setCountryCode(normalizeCountryCode(request.countryCode()));
        entity.setLocality(blankToNull(request.locality()));
        entity.setActive(request.active() == null || request.active());
        entity.setStatus(entity.isActive() ? CustomerStatus.ACTIVE : CustomerStatus.INACTIVE);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeTaxIdentifier(String value) {
        String clean = blankToNull(value);
        return clean == null ? null : clean.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
    }

    private String normalizeCountryCode(String value) {
        String clean = blankToNull(value);
        return clean == null ? null : clean.toUpperCase(java.util.Locale.ROOT);
    }
}
