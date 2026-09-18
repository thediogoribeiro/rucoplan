package pt.rucodel.productionplanning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import pt.rucodel.productionplanning.domain.CustomerStatus;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
        name = "customer_reference",
        indexes = {
                @Index(name = "idx_customer_reference_external_id", columnList = "external_id"),
                @Index(name = "idx_customer_reference_customer_number", columnList = "customer_number"),
                @Index(name = "idx_customer_reference_name", columnList = "name"),
                @Index(name = "idx_customer_reference_normalized_name", columnList = "normalized_name"),
                @Index(name = "idx_customer_reference_external_ref", columnList = "external_system, external_customer_id")
        }
)
public class CustomerReferenceEntity extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "external_id", length = 120, unique = true)
    private String externalId;

    @Column(name = "customer_number", unique = true)
    private Integer customerNumber;

    @Column(name = "external_system", length = 80)
    private String externalSystem;

    @Column(name = "external_customer_id", length = 160)
    private String externalCustomerId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 255)
    private String normalizedName;

    @Column(name = "tax_identifier", length = 80)
    private String taxIdentifier;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "locality", length = 120)
    private String locality;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Override
    protected void assignIdIfNecessary() {
        if (id == null) {
            id = newId();
        }
    }

    @PrePersist
    @PreUpdate
    void normalizeNameBeforeSave() {
        if ((normalizedName == null || normalizedName.isBlank()) && name != null) {
            normalizedName = normalizeForSearch(name);
        }
    }

    private String normalizeForSearch(String value) {
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replace('’', '\'')
                .replaceAll("[\\p{Punct}&&[^'-]]+", " ")
                .replace('-', ' ')
                .replace('\'', ' ')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT)
                .trim();
        return normalized;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public Integer getCustomerNumber() {
        return customerNumber;
    }

    public void setCustomerNumber(Integer customerNumber) {
        this.customerNumber = customerNumber;
    }

    public String getExternalSystem() {
        return externalSystem;
    }

    public void setExternalSystem(String externalSystem) {
        this.externalSystem = externalSystem;
    }

    public String getExternalCustomerId() {
        return externalCustomerId;
    }

    public void setExternalCustomerId(String externalCustomerId) {
        this.externalCustomerId = externalCustomerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public String getTaxIdentifier() {
        return taxIdentifier;
    }

    public void setTaxIdentifier(String taxIdentifier) {
        this.taxIdentifier = taxIdentifier;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public void setStatus(CustomerStatus status) {
        this.status = status;
        this.active = status == null || status == CustomerStatus.ACTIVE;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        this.status = active ? CustomerStatus.ACTIVE : CustomerStatus.INACTIVE;
    }

    public long getVersion() {
        return version;
    }
}
