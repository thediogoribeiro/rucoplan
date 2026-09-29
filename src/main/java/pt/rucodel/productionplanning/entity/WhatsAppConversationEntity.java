package pt.rucodel.productionplanning.entity;

import jakarta.persistence.*;
import pt.rucodel.productionplanning.domain.FactoryTimeSlot;
import pt.rucodel.productionplanning.domain.WhatsAppConversationState;
import pt.rucodel.productionplanning.domain.WheelType;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "whatsapp_conversation",
        indexes = {
                @Index(name = "idx_whatsapp_conversation_site_state", columnList = "production_site_id, state"),
                @Index(name = "idx_whatsapp_conversation_state", columnList = "state"),
                @Index(name = "idx_whatsapp_conversation_customer", columnList = "customer_reference_id")
        }
)
public class WhatsAppConversationEntity extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_site_id")
    private ProductionSiteEntity productionSite;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "messaging_identity_id", nullable = false, unique = true)
    private MessagingIdentityEntity messagingIdentity;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 80)
    private WhatsAppConversationState state = WhatsAppConversationState.AWAITING_CUSTOMER_NAME;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_reference_id")
    private CustomerReferenceEntity customer;

    @Column(name = "new_customer_name")
    private String newCustomerName;

    @Column(name = "new_customer_tax_identifier", length = 80)
    private String newCustomerTaxIdentifier;

    @Column(name = "new_customer_country_code", length = 2)
    private String newCustomerCountryCode;

    @Column(name = "new_customer_locality", length = 120)
    private String newCustomerLocality;

    @Column(name = "candidate_customer_ids", length = 2000)
    private String candidateCustomerIds;

    @Column(name = "bipartite_quantity")
    private Integer bipartiteQuantity;

    @Column(name = "washed_quantity")
    private Integer washedQuantity;

    @Column(name = "normal_quantity")
    private Integer normalQuantity;

    @Column(name = "factory_dropoff_date")
    private LocalDate factoryDropoffDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "factory_dropoff_slot", length = 40)
    private FactoryTimeSlot factoryDropoffSlot;

    @Column(name = "ready_date")
    private LocalDate readyDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "factory_pickup_slot", length = 40)
    private FactoryTimeSlot factoryPickupSlot;

    @Column(name = "notes", length = 2000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_request_id")
    private WheelIntakeRequestEntity confirmedRequest;

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

    public ProductionSiteEntity getProductionSite() {
        return productionSite;
    }

    public void setProductionSite(ProductionSiteEntity productionSite) {
        this.productionSite = productionSite;
    }

    public MessagingIdentityEntity getMessagingIdentity() {
        return messagingIdentity;
    }

    public void setMessagingIdentity(MessagingIdentityEntity messagingIdentity) {
        this.messagingIdentity = messagingIdentity;
    }

    public WhatsAppConversationState getState() {
        return state;
    }

    public void setState(WhatsAppConversationState state) {
        this.state = state;
    }

    public CustomerReferenceEntity getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerReferenceEntity customer) {
        this.customer = customer;
    }

    public String getNewCustomerName() {
        return newCustomerName;
    }

    public void setNewCustomerName(String newCustomerName) {
        this.newCustomerName = newCustomerName;
    }

    public String getNewCustomerTaxIdentifier() {
        return newCustomerTaxIdentifier;
    }

    public void setNewCustomerTaxIdentifier(String newCustomerTaxIdentifier) {
        this.newCustomerTaxIdentifier = newCustomerTaxIdentifier;
    }

    public String getNewCustomerCountryCode() {
        return newCustomerCountryCode;
    }

    public void setNewCustomerCountryCode(String newCustomerCountryCode) {
        this.newCustomerCountryCode = newCustomerCountryCode;
    }

    public String getNewCustomerLocality() {
        return newCustomerLocality;
    }

    public void setNewCustomerLocality(String newCustomerLocality) {
        this.newCustomerLocality = newCustomerLocality;
    }

    public String getCandidateCustomerIds() {
        return candidateCustomerIds;
    }

    public void setCandidateCustomerIds(String candidateCustomerIds) {
        this.candidateCustomerIds = candidateCustomerIds;
    }

    public LocalDate getFactoryDropoffDate() {
        return factoryDropoffDate;
    }

    public void setFactoryDropoffDate(LocalDate factoryDropoffDate) {
        this.factoryDropoffDate = factoryDropoffDate;
    }

    public FactoryTimeSlot getFactoryDropoffSlot() {
        return factoryDropoffSlot;
    }

    public void setFactoryDropoffSlot(FactoryTimeSlot factoryDropoffSlot) {
        this.factoryDropoffSlot = factoryDropoffSlot;
    }

    public LocalDate getReadyDate() {
        return readyDate;
    }

    public void setReadyDate(LocalDate readyDate) {
        this.readyDate = readyDate;
    }

    public FactoryTimeSlot getFactoryPickupSlot() {
        return factoryPickupSlot;
    }

    public void setFactoryPickupSlot(FactoryTimeSlot factoryPickupSlot) {
        this.factoryPickupSlot = factoryPickupSlot;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public WheelIntakeRequestEntity getConfirmedRequest() {
        return confirmedRequest;
    }

    public void setConfirmedRequest(WheelIntakeRequestEntity confirmedRequest) {
        this.confirmedRequest = confirmedRequest;
    }

    public Integer wheelQuantity(WheelType type) {
        return switch (type) {
            case BIPARTITE -> bipartiteQuantity;
            case WASHED -> washedQuantity;
            case NORMAL -> normalQuantity;
        };
    }

    public void setWheelQuantity(WheelType type, Integer value) {
        switch (type) {
            case BIPARTITE -> bipartiteQuantity = value;
            case WASHED -> washedQuantity = value;
            case NORMAL -> normalQuantity = value;
        }
    }

    public Map<WheelType, Integer> wheelQuantityMap() {
        Map<WheelType, Integer> quantities = new EnumMap<>(WheelType.class);
        quantities.put(WheelType.BIPARTITE, bipartiteQuantity == null ? 0 : bipartiteQuantity);
        quantities.put(WheelType.WASHED, washedQuantity == null ? 0 : washedQuantity);
        quantities.put(WheelType.NORMAL, normalQuantity == null ? 0 : normalQuantity);
        return quantities;
    }

    public int totalWheelQuantity() {
        return wheelQuantityMap().values().stream().mapToInt(Integer::intValue).sum();
    }

    public long getVersion() {
        return version;
    }
}
