package pt.rucodel.productionplanning.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramMessage(
        @JsonProperty("message_id") Long messageId,
        TelegramUser from,
        TelegramChat chat,
        String text,
        TelegramContact contact
) {
    public TelegramMessage(Long messageId, TelegramUser from, TelegramChat chat, String text) {
        this(messageId, from, chat, text, null);
    }
}
