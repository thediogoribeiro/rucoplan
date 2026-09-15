package pt.rucodel.productionplanning.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramMessage(
        @JsonProperty("message_id") Long messageId,
        TelegramUser from,
        TelegramChat chat,
        String text
) {
}
