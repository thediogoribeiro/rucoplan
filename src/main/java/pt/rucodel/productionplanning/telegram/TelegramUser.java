package pt.rucodel.productionplanning.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUser(
        Long id,
        String username,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName
) {
}
