package pt.rucodel.productionplanning.telegram;

public record TelegramButton(String text, String callbackData, boolean requestContact) {
    public TelegramButton(String text, String callbackData) {
        this(text, callbackData, false);
    }

    public static TelegramButton requestContact(String text) {
        return new TelegramButton(text, null, true);
    }
}
