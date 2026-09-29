package pt.rucodel.productionplanning.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.integrations.telegram")
public record TelegramProperties(
        boolean enabled,
        String botToken,
        String botUsername,
        String webhookSecret,
        String webhookBaseUrl,
        boolean webhookAutoRegister
) {
    public boolean hasWebhookConfiguration() {
        return enabled
                && botToken != null && !botToken.isBlank()
                && webhookSecret != null && !webhookSecret.isBlank()
                && webhookBaseUrl != null && !webhookBaseUrl.isBlank();
    }

    public String webhookUrl() {
        if (webhookBaseUrl == null || webhookBaseUrl.isBlank()) {
            return null;
        }
        return webhookBaseUrl.replaceAll("/+$", "") + "/api/v1/integrations/telegram/webhook";
    }
}
