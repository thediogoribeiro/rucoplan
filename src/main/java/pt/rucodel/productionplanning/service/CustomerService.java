package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.dto.CustomerRequest;
import pt.rucodel.productionplanning.dto.CustomerResponse;
import pt.rucodel.productionplanning.domain.CustomerStatus;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.entity.CustomerReferenceEntity;
import pt.rucodel.productionplanning.entity.ProductionSiteEntity;
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
    private final ProductionSiteService productionSites;

    public CustomerService(CustomerReferenceRepository customers, CustomerDirectoryPort customerDirectory, ApiMapper mapper,
                           CustomerNameNormalizer normalizer, PublicCodeService publicCodes,
                           ProductionSiteService productionSites) {
        this.customers = customers;
        this.customerDirectory = customerDirectory;
        this.mapper = mapper;
        this.normalizer = normalizer;
        this.publicCodes = publicCodes;
        this.productionSites = productionSites;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> search(String query, int limit) {
        return search(ProductionSiteCode.PT, query, limit);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> search(ProductionSiteCode siteCode, String query, int limit) {
        String effectiveQuery = query == null ? "" : query.trim();
        List<CustomerReferenceEntity> result = effectiveQuery.isBlank()
                ? customers.findActiveForSite(siteCode)
                : customers.searchForSite(siteCode, effectiveQuery);
        return result.stream()
                .limit(Math.max(1, Math.min(limit, 50)))
                .map(mapper::toCustomer)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> list() {
        return list(ProductionSiteCode.PT);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> list(ProductionSiteCode siteCode) {
        return customers.findActiveForSite(siteCode).stream().map(mapper::toCustomer).toList();
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request, String actor) {
        return create(ProductionSiteCode.PT, request, actor);
    }

    @Transactional
    public CustomerResponse create(ProductionSiteCode siteCode, CustomerRequest request, String actor) {
        ProductionSiteEntity site = productionSites.requireActive(siteCode);
        CustomerReferenceEntity entity = new CustomerReferenceEntity();
        entity.setProductionSite(site);
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
        return update(ProductionSiteCode.PT, id, request, actor);
    }

    @Transactional
    public CustomerResponse update(ProductionSiteCode siteCode, UUID id, CustomerRequest request, String actor) {
        CustomerReferenceEntity entity = customers.findByIdForSite(siteCode, id)
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

    public CustomerReferenceEntity requireEntity(ProductionSiteCode siteCode, UUID id) {
        return customers.findByIdForSite(siteCode, id).filter(CustomerReferenceEntity::isActive)
                .orElseThrow(() -> new EntityNotFoundException("Customer was not found."));
    }

    private void apply(CustomerReferenceEntity entity, CustomerRequest request) {
        entity.setExternalId(blankToNull(request.externalId()));
        entity.setExternalSystem(blankToNull(request.externalSystem()));
        entity.setExternalCustomerId(blankToNull(request.externalCustomerId()));
        if (entity.getExternalSystem() != null && entity.getExternalCustomerId() != null) {
            customers.findExternalReferenceForSite(entity.getProductionSite().getCode(),
                            entity.getExternalSystem(), entity.getExternalCustomerId())
                    .filter(existing -> !existing.getId().equals(entity.getId()))
                    .ifPresent(existing -> {
                        throw new InvalidRequestException("External customer reference is already linked to another customer.");
                    });
        }
        entity.setName(request.name().trim());
        entity.setNormalizedName(normalizer.normalize(request.name()));
        entity.setTaxIdentifier(normalizeTaxIdentifier(request.taxIdentifier()));
        entity.setCountryCode(normalizeCountryCode(request.countryCode()));
        entity.setCountryName(normalizeCountryName(request.countryName(), entity.getCountryCode()));
        entity.setLocality(blankToNull(request.locality()));
        entity.setActive(request.active() == null || request.active());
        entity.setStatus(entity.isActive() ? CustomerStatus.ACTIVE : CustomerStatus.INACTIVE);
        if (entity.getTaxIdentifier() != null
                && customers.existsActiveByNormalizedNameAndTaxIdentifierForSite(
                entity.getProductionSite().getCode(),
                entity.getNormalizedName(),
                entity.getTaxIdentifier(),
                entity.getId())) {
            throw new InvalidRequestException("CUSTOMER_DUPLICATE_NAME_VAT",
                    "Já existe um cliente com este nome e NIF nesta unidade.");
        }
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

    private String normalizeCountryName(String value, String countryCode) {
        String clean = blankToNull(value);
        if (clean == null && countryCode != null) {
            clean = switch (countryCode) {
                case "PT" -> "Portugal";
                case "LU" -> "Luxemburgo";
                case "FR" -> "França";
                case "ES" -> "Espanha";
                default -> countryCode;
            };
        }
        if (clean == null) {
            throw new InvalidRequestException("CUSTOMER_COUNTRY_REQUIRED", "O país do cliente é obrigatório.");
        }
        if (clean.length() > 120) {
            throw new InvalidRequestException("CUSTOMER_COUNTRY_TOO_LONG", "O país do cliente não pode exceder 120 caracteres.");
        }
        return clean;
    }
}
