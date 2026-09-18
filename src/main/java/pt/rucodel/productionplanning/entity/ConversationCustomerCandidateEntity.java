package pt.rucodel.productionplanning.entity;

import jakarta.persistence.*;
import pt.rucodel.productionplanning.domain.ConversationCustomerOptionType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "conversation_customer_candidate",
        indexes = {
                @Index(name = "idx_conversation_customer_candidate_conversation", columnList = "conversation_id"),
                @Index(name = "idx_conversation_customer_candidate_customer", columnList = "customer_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_conversation_customer_candidate_position", columnNames = {"conversation_id", "position"})
        }
)
public class ConversationCustomerCandidateEntity extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private TelegramConversationEntity conversation;

    @Column(name = "position", nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_type", nullable = false, length = 40)
    private ConversationCustomerOptionType optionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private CustomerReferenceEntity customer;

    @Column(name = "customer_name_snapshot", length = 255)
    private String customerNameSnapshot;

    @Column(name = "original_search_text", nullable = false, length = 255)
    private String originalSearchText;

    @Column(name = "normalized_search_text", nullable = false, length = 255)
    private String normalizedSearchText;

    @Column(name = "similarity_score")
    private Double similarityScore;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

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

    public TelegramConversationEntity getConversation() {
        return conversation;
    }

    public void setConversation(TelegramConversationEntity conversation) {
        this.conversation = conversation;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public ConversationCustomerOptionType getOptionType() {
        return optionType;
    }

    public void setOptionType(ConversationCustomerOptionType optionType) {
        this.optionType = optionType;
    }

    public CustomerReferenceEntity getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerReferenceEntity customer) {
        this.customer = customer;
    }

    public String getCustomerNameSnapshot() {
        return customerNameSnapshot;
    }

    public void setCustomerNameSnapshot(String customerNameSnapshot) {
        this.customerNameSnapshot = customerNameSnapshot;
    }

    public String getOriginalSearchText() {
        return originalSearchText;
    }

    public void setOriginalSearchText(String originalSearchText) {
        this.originalSearchText = originalSearchText;
    }

    public String getNormalizedSearchText() {
        return normalizedSearchText;
    }

    public void setNormalizedSearchText(String normalizedSearchText) {
        this.normalizedSearchText = normalizedSearchText;
    }

    public Double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(Double similarityScore) {
        this.similarityScore = similarityScore;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public long getVersion() {
        return version;
    }
}
