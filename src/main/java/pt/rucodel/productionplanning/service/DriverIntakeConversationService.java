package pt.rucodel.productionplanning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pt.rucodel.productionplanning.domain.WheelType;
import pt.rucodel.productionplanning.entity.TelegramIntakeDraftEntity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class DriverIntakeConversationService {
    private static final int BIPARTITE_MINIMUM_BUSINESS_DAYS = 15;

    private final ZoneId businessZone;
    private final LocalTime overnightEndTime;

    public DriverIntakeConversationService(AppProperties appProperties,
                                           @Value("${app.planning.overnight-end-time:06:00}") String overnightEndTime) {
        this.businessZone = ZoneId.of(appProperties.timezone());
        this.overnightEndTime = LocalTime.parse(overnightEndTime);
    }

    public String customerQuestion() {
        return "1/10 — Qual é o cliente?";
    }

    public String quantityQuestion(WheelType type) {
        return switch (type) {
            case BIPARTITE -> """
                    2/10 — Quantas jantes bipartidas vão ser levantadas?

                    Se não existirem jantes bipartidas, responda 0.""";
            case WASHED -> """
                    3/10 — Quantas jantes lavadas vão ser levantadas?

                    Se não existirem jantes lavadas, responda 0.""";
            case NORMAL -> """
                    4/10 — Quantas jantes normais vão ser levantadas?

                    Consideram-se normais as restantes jantes que não são bipartidas nem lavadas. Se não existirem, responda 0.""";
        };
    }

    public Integer parseQuantity(String text) {
        try {
            int quantity = Integer.parseInt(text == null ? "" : text.trim());
            return quantity < 0 ? null : quantity;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public String summary(TelegramIntakeDraftEntity draft, DateTimeFormatter formatter) {
        return """
                10/10 — Confirma o pedido?

                Cliente: %s

                Tipos de jantes:
                * Bipartidas: %d
                * Lavadas: %d
                * Normais: %d
                * Total: %d jantes

                Entrada prevista na fábrica: %s, %s
                Devem estar prontas: %s, %s
                Notas: %s
                %s
                """.formatted(
                draft.getCustomerNameSnapshot(),
                value(draft, WheelType.BIPARTITE),
                value(draft, WheelType.WASHED),
                value(draft, WheelType.NORMAL),
                draft.totalWheelQuantity(),
                formatter.format(draft.getFactoryDropoffDate()),
                draft.getFactoryDropoffSlot().label(),
                formatter.format(draft.getReadyDate()),
                draft.getFactoryPickupSlot().label(),
                draft.getNotes() == null ? "Sem notas" : draft.getNotes(),
                bipartiteDeadlineAdjustmentMessage(draft, formatter)
        );
    }

    private String bipartiteDeadlineAdjustmentMessage(TelegramIntakeDraftEntity draft, DateTimeFormatter formatter) {
        if (value(draft, WheelType.BIPARTITE) <= 0
                || draft.getFactoryDropoffDate() == null
                || draft.getFactoryDropoffSlot() == null
                || draft.getReadyDate() == null
                || draft.getFactoryPickupSlot() == null) {
            return "";
        }
        OffsetDateTime available = draft.getFactoryDropoffSlot().endAt(draft.getFactoryDropoffDate(), businessZone, overnightEndTime);
        OffsetDateTime requested = draft.getFactoryPickupSlot().startAt(draft.getReadyDate(), businessZone);
        OffsetDateTime minimum = minimumBipartiteDeadline(available, requested);
        if (!minimum.isAfter(requested)) {
            return "";
        }
        return "\nO prazo das jantes bipartidas foi ajustado para "
                + formatter.format(minimum.atZoneSameInstant(businessZone).toLocalDate())
                + ", devido ao prazo mínimo de 15 dias úteis.";
    }

    private OffsetDateTime minimumBipartiteDeadline(OffsetDateTime available, OffsetDateTime requestedDeadline) {
        LocalDate date = available.atZoneSameInstant(businessZone).toLocalDate();
        int remaining = BIPARTITE_MINIMUM_BUSINESS_DAYS;
        while (remaining > 0) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                remaining--;
            }
        }
        return date.atTime(requestedDeadline.atZoneSameInstant(businessZone).toLocalTime())
                .atZone(businessZone)
                .toOffsetDateTime();
    }

    public String wheelBreakdown(java.util.Map<WheelType, Integer> quantities) {
        return String.format(Locale.ROOT, "%d bipartidas · %d lavadas · %d normais",
                quantities.getOrDefault(WheelType.BIPARTITE, 0),
                quantities.getOrDefault(WheelType.WASHED, 0),
                quantities.getOrDefault(WheelType.NORMAL, 0));
    }

    private int value(TelegramIntakeDraftEntity draft, WheelType type) {
        Integer value = draft.wheelQuantity(type);
        return value == null ? 0 : value;
    }
}
