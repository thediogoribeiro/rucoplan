package pt.rucodel.productionplanning.dto;

import pt.rucodel.productionplanning.domain.IngestionStatus;

import java.util.UUID;

public record WhatsAppIngestionResponse(
        UUID ingestionId,
        IngestionStatus status,
        UUID requestId,
        String externalMessageId,
        String message
) {
}
