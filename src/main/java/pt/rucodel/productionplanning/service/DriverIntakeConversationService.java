package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import pt.rucodel.productionplanning.domain.WheelType;
import pt.rucodel.productionplanning.entity.TelegramIntakeDraftEntity;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class DriverIntakeConversationService {
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
                draft.getNotes() == null ? "Sem notas" : draft.getNotes()
        );
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
