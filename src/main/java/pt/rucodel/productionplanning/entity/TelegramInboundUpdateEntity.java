package pt.rucodel.productionplanning.entity;

import jakarta.persistence.*;
import pt.rucodel.productionplanning.domain.TelegramInboundProcessingStatus;

import java.time.OffsetDateTime;

@Entity
@Table(name = "telegram_inbound_update")
public class TelegramInboundUpdateEntity {
    @Id
    @Column(name = "update_id", nullable = false)
    private Long updateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private TelegramInboundProcessingStatus status = TelegramInboundProcessingStatus.RECEIVED;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "error_summary", length = 1000)
    private String errorSummary;

    public Long getUpdateId() {
        return updateId;
    }

    public void setUpdateId(Long updateId) {
        this.updateId = updateId;
    }

    public TelegramInboundProcessingStatus getStatus() {
        return status;
    }

    public void setStatus(TelegramInboundProcessingStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
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

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }
}
