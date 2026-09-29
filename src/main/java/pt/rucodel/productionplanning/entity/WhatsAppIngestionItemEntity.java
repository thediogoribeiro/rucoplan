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

    @Column(name = "driver_external_id", length = 120)
    private String driverExternalId;

    @Column(name = "customer_external_id", length = 120)
    private String customerExternalId;

    @Column(name = "customer_name")
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private IngestionStatus status;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "whatsapp_business_account_id", length = 160)
    private String whatsappBusinessAccountId;

    @Column(name = "phone_number_id", length = 160)
    private String phoneNumberId;

    @Column(name = "sender_wa_id", length = 160)
    private String senderWaId;

    @Column(name = "message_type", length = 80)
    private String messageType;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "payload_hash", length = 128)
    private String payloadHash;

    @Column(name = "outbound_message_id", length = 180)
    private String outboundMessageId;

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

    public String getWhatsappBusinessAccountId() {
        return whatsappBusinessAccountId;
    }

    public void setWhatsappBusinessAccountId(String whatsappBusinessAccountId) {
        this.whatsappBusinessAccountId = whatsappBusinessAccountId;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getSenderWaId() {
        return senderWaId;
    }

    public void setSenderWaId(String senderWaId) {
        this.senderWaId = senderWaId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(OffsetDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public String getOutboundMessageId() {
        return outboundMessageId;
    }

    public void setOutboundMessageId(String outboundMessageId) {
        this.outboundMessageId = outboundMessageId;
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
