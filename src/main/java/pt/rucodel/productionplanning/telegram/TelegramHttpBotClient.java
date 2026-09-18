package pt.rucodel.productionplanning.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class TelegramHttpBotClient implements TelegramBotClient {
    private final boolean enabled;
    private final String token;
    private final RestClient restClient;

    public TelegramHttpBotClient(@Value("${app.integrations.telegram.enabled:false}") boolean enabled,
                                 @Value("${app.integrations.telegram.bot-token:}") String token,
                                 RestClient.Builder builder) {
        this.enabled = enabled;
        this.token = token;
        this.restClient = builder.baseUrl("https://api.telegram.org").build();
    }

    @Override
    public void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    @Override
    public void sendMessage(Long chatId, String text, List<List<TelegramButton>> inlineKeyboard) {
        if (!enabled || token == null || token.isBlank()) {
            return;
        }
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        if (inlineKeyboard != null && !inlineKeyboard.isEmpty()) {
            boolean replyKeyboard = inlineKeyboard.stream().flatMap(List::stream).anyMatch(TelegramButton::requestContact);
            if (replyKeyboard) {
                body.put("reply_markup", Map.of(
                        "keyboard", inlineKeyboard.stream()
                                .map(row -> row.stream()
                                        .map(button -> button.requestContact()
                                                ? Map.<String, Object>of("text", button.text(), "request_contact", true)
                                                : Map.<String, Object>of("text", button.text()))
                                        .toList())
                                .toList(),
                        "resize_keyboard", true,
                        "one_time_keyboard", true
                ));
            } else {
                body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard.stream()
                        .map(row -> row.stream()
                                .map(button -> Map.of("text", button.text(), "callback_data", button.callbackData()))
                                .toList())
                        .toList()));
            }
        }
        restClient.post()
                .uri("/bot{token}/sendMessage", token)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void answerCallbackQuery(String callbackQueryId) {
        if (!enabled || token == null || token.isBlank() || callbackQueryId == null || callbackQueryId.isBlank()) {
            return;
        }
        restClient.post()
                .uri("/bot{token}/answerCallbackQuery", token)
                .body(Map.of("callback_query_id", callbackQueryId))
                .retrieve()
                .toBodilessEntity();
    }
}
