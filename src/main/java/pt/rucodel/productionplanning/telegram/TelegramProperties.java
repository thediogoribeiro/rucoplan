package pt.rucodel.productionplanning.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.integrations.telegram")
public record TelegramProperties(
        boolean enabled,
        boolean webhookAutoRegister,
        String testBotToken,
        String testBotUsername,
        String testWebhookSecret,
        String testWebhookBaseUrl,
        String productionBotToken,
        String productionBotUsername,
        String productionWebhookSecret,
        String productionWebhookBaseUrl
) {
    public boolean hasWebhookConfiguration(String environment) {
        return enabled
                && !blank(botToken(environment))
                && !blank(webhookSecret(environment))
                && !blank(webhookBaseUrl(environment));
    }

    public String webhookUrl(String environment) {
        String baseUrl = webhookBaseUrl(environment);
        if (blank(baseUrl)) {
            return null;
        }
        return baseUrl.replaceAll("/+$", "") + "/api/v1/integrations/telegram/webhook";
    }

    public String botToken(String environment) {
        return isProduction(environment) ? productionBotToken : testBotToken;
    }

    public String botUsername(String environment) {
        return isProduction(environment) ? productionBotUsername : testBotUsername;
    }

    public String webhookSecret(String environment) {
        return isProduction(environment) ? productionWebhookSecret : testWebhookSecret;
    }

    public String webhookBaseUrl(String environment) {
        return isProduction(environment) ? productionWebhookBaseUrl : testWebhookBaseUrl;
    }

    public String botKind(String environment) {
        return isProduction(environment) ? "production" : "test";
    }

    public boolean isProduction(String environment) {
        return "production".equalsIgnoreCase(environment == null ? "" : environment.trim());
    }

    public void validateActiveEnvironment(String environment) {
        if (!enabled) {
            return;
        }
        String bot = botKind(environment);
        if (blank(botToken(environment))) {
            throw new IllegalStateException("Telegram " + bot + " bot token is required when TELEGRAM_ENABLED=true.");
        }
        if (blank(botUsername(environment))) {
            throw new IllegalStateException("Telegram " + bot + " bot username is required when TELEGRAM_ENABLED=true.");
        }
        if (blank(webhookSecret(environment))) {
            throw new IllegalStateException("Telegram " + bot + " webhook secret is required when TELEGRAM_ENABLED=true.");
        }
        if (blank(webhookBaseUrl(environment)) && webhookAutoRegister) {
            throw new IllegalStateException("Telegram " + bot + " webhook URL is required when webhook auto-registration is enabled.");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
