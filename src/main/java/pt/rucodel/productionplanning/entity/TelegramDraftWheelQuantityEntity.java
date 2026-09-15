package pt.rucodel.productionplanning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import pt.rucodel.productionplanning.domain.WheelType;

import java.util.UUID;

@Entity
@Table(
        name = "telegram_intake_draft_wheel_quantity",
        uniqueConstraints = @UniqueConstraint(name = "uk_telegram_draft_wheel_quantity_type", columnNames = {"draft_id", "wheel_type"}),
        indexes = @Index(name = "idx_telegram_draft_wheel_quantity_draft", columnList = "draft_id")
)
public class TelegramDraftWheelQuantityEntity extends BaseEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_id", nullable = false)
    private TelegramIntakeDraftEntity draft;

    @Enumerated(EnumType.STRING)
    @Column(name = "wheel_type", nullable = false, length = 40)
    private WheelType wheelType;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Override
    protected void assignIdIfNecessary() {
        if (id == null) {
            id = newId();
        }
    }

    public UUID getId() {
        return id;
    }

    public TelegramIntakeDraftEntity getDraft() {
        return draft;
    }

    public void setDraft(TelegramIntakeDraftEntity draft) {
        this.draft = draft;
    }

    public WheelType getWheelType() {
        return wheelType;
    }

    public void setWheelType(WheelType wheelType) {
        this.wheelType = wheelType;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
