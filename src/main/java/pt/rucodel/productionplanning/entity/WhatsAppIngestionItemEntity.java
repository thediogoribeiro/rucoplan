package pt.rucodel.productionplanning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import pt.rucodel.productionplanning.domain.IngestionStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "whatsapp_ingestion_item",
        indexes = {
                @Index(name = "idx_whatsapp_ingestion_status", columnList = "status"),
                @Index(name = "idx_whatsapp_ingestion_message", columnList = "external_message_id")
        }
)
public class WhatsAppIngestionItemEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "external_message_id", nullable = false, unique = true, length = 180)
    private String externalMessageId;

    @Column(name = "driver_external_id", nullable = false, length = 120)
    private String driverExternalId;

    @Column(name = "customer_external_id", length = 120)
    private String customerExternalId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private IngestionStatus status;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public String getExternalMessageId() {
        return externalMessageId;
    }

    public void setExternalMessageId(String externalMessageId) {
        this.externalMessageId = externalMessageId;
    }

    public String getDriverExternalId() {
        return driverExternalId;
    }

    public void setDriverExternalId(String driverExternalId) {
        this.driverExternalId = driverExternalId;
    }

    public String getCustomerExternalId() {
        return customerExternalId;
    }

    public void setCustomerExternalId(String customerExternalId) {
        this.customerExternalId = customerExternalId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public IngestionStatus getStatus() {
        return status;
    }

    public void setStatus(IngestionStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
