package pt.rucodel.productionplanning.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
public class WhatsAppInboundMessageMapper implements InboundMessagingAdapter {
    private final ObjectMapper objectMapper;

    public WhatsAppInboundMessageMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ConversationMessage> map(String rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            List<ConversationMessage> result = new ArrayList<>();
            for (JsonNode entry : root.path("entry")) {
                String wabaId = text(entry.path("id"));
                for (JsonNode change : entry.path("changes")) {
                    JsonNode value = change.path("value");
                    String phoneNumberId = text(value.path("metadata").path("phone_number_id"));
                    String profileName = firstContactName(value);
                    for (JsonNode message : value.path("messages")) {
                        String type = text(message.path("type"));
                        result.add(new ConversationMessage(
                                text(message.path("id")),
                                text(message.path("from")),
                                text(message.path("from")),
                                profileName,
                                phoneNumberId,
                                wabaId,
                                type,
                                messageText(message, type),
                                receivedAt(message)
                        ));
                    }
                }
            }
            return result.stream()
                    .filter(message -> message.externalMessageId() != null && message.fromWaId() != null)
                    .toList();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid WhatsApp webhook payload.", ex);
        }
    }

    private String firstContactName(JsonNode value) {
        JsonNode contacts = value.path("contacts");
        if (!contacts.isArray() || contacts.isEmpty()) {
            return null;
        }
        return text(contacts.get(0).path("profile").path("name"));
    }

    private String messageText(JsonNode message, String type) {
        if ("text".equals(type)) {
            return text(message.path("text").path("body"));
        }
        if ("button".equals(type)) {
            return text(message.path("button").path("payload"), text(message.path("button").path("text")));
        }
        if ("interactive".equals(type)) {
            JsonNode interactive = message.path("interactive");
            if (interactive.has("button_reply")) {
                return text(interactive.path("button_reply").path("id"),
                        text(interactive.path("button_reply").path("title")));
            }
            if (interactive.has("list_reply")) {
                return text(interactive.path("list_reply").path("id"),
                        text(interactive.path("list_reply").path("title")));
            }
        }
        return "";
    }

    private OffsetDateTime receivedAt(JsonNode message) {
        String timestamp = text(message.path("timestamp"));
        if (timestamp == null) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(timestamp)).atOffset(ZoneOffset.UTC);
        } catch (NumberFormatException ex) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    private String text(JsonNode node) {
        return text(node, null);
    }

    private String text(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? fallback : value;
    }
}
