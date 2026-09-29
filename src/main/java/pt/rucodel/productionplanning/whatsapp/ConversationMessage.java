package pt.rucodel.productionplanning.whatsapp;

import java.time.OffsetDateTime;

public record ConversationMessage(
        String externalMessageId,
        String fromWaId,
        String phoneNumber,
        String profileName,
        String phoneNumberId,
        String whatsappBusinessAccountId,
        String messageType,
        String text,
        OffsetDateTime receivedAt
) {
}
