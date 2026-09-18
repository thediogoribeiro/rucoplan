package pt.rucodel.productionplanning.entity;

import jakarta.persistence.*;
import pt.rucodel.productionplanning.domain.CustomerRegistrationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "customer_registration_request",
        indexes = {
                @Index(name = "idx_customer_registration_status", columnList = "status"),
                @Index(name = "idx_customer_registration_normalized", columnList = "normalized_name"),
                @Index(name = "idx_customer_registration_driver", columnList = "requested_by_driver_id")
        }
)
public class CustomerRegistrationRequestEntity extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "proposed_name", nullable = false, length = 255)
    private String proposedName;

    @Column(name = "normalized_name", nullable = false, length = 255)
    private String normalizedName;

    @Column(name = "customer_number", length = 120)
    private String customerNumber;

    @Column(name = "reserved_customer_number")
    private Integer reservedCustomerNumber;

    @Column(name = "tax_identifier", length = 80)
    private String taxIdentifier;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "locality", length = 120)
    private String locality;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_driver_id", nullable = false)
    private DriverEntity requestedByDriver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_identity_id")
    private MessagingIdentityEntity requestedByIdentity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private TelegramConversationEntity conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CustomerRegistrationStatus status = CustomerRegistrationStatus.PENDING_REVIEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_customer_id")
    private CustomerReferenceEntity matchedCustomer;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "reviewed_by", length = 160)
    private String reviewedBy;

    @Column(name = "review_notes", length = 1000)
    private String reviewNotes;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Override
    protected void assignIdIfNecessary() {
        if (id == null) {
            id = newId();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getProposedName() {
        return proposedName;
    }

    public void setProposedName(String proposedName) {
        this.proposedName = proposedName;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public String getCustomerNumber() {
        return customerNumber;
    }

    public void setCustomerNumber(String customerNumber) {
        this.customerNumber = customerNumber;
    }

    public Integer getReservedCustomerNumber() {
        return reservedCustomerNumber;
    }

    public void setReservedCustomerNumber(Integer reservedCustomerNumber) {
        this.reservedCustomerNumber = reservedCustomerNumber;
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

    public DriverEntity getRequestedByDriver() {
        return requestedByDriver;
    }

    public void setRequestedByDriver(DriverEntity requestedByDriver) {
        this.requestedByDriver = requestedByDriver;
    }

    public MessagingIdentityEntity getRequestedByIdentity() {
        return requestedByIdentity;
    }

    public void setRequestedByIdentity(MessagingIdentityEntity requestedByIdentity) {
        this.requestedByIdentity = requestedByIdentity;
    }

    public TelegramConversationEntity getConversation() {
        return conversation;
    }

    public void setConversation(TelegramConversationEntity conversation) {
        this.conversation = conversation;
    }

    public CustomerRegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(CustomerRegistrationStatus status) {
        this.status = status;
    }

    public CustomerReferenceEntity getMatchedCustomer() {
        return matchedCustomer;
    }

    public void setMatchedCustomer(CustomerReferenceEntity matchedCustomer) {
        this.matchedCustomer = matchedCustomer;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(OffsetDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }

    public long getVersion() {
        return version;
    }
}
