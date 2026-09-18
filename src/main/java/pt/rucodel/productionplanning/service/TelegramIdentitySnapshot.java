package pt.rucodel.productionplanning.service;

public record TelegramIdentitySnapshot(
        Long userId,
        Long chatId,
        String username,
        String firstName,
        String lastName,
        String languageCode
) {
}
