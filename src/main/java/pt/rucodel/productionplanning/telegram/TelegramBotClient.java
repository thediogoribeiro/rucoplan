package pt.rucodel.productionplanning.telegram;

import java.util.List;

public interface TelegramBotClient {
    void sendMessage(Long chatId, String text);

    void sendMessage(Long chatId, String text, List<List<TelegramButton>> inlineKeyboard);

    void answerCallbackQuery(String callbackQueryId);
}
