package pt.rucodel.productionplanning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
        name = "customer_reference",
        indexes = {
                @Index(name = "idx_customer_reference_external_id", columnList = "external_id"),
                @Index(name = "idx_customer_reference_name", columnList = "name"),
                @Index(name = "idx_customer_reference_normalized_name", columnList = "normalized_name")
        }
)
public class CustomerReferenceEntity extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "external_id", length = 120, unique = true)
    private String externalId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 255)
    private String normalizedName;

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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getVersion() {
        return version;
    }
}
