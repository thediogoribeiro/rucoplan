package pt.rucodel.productionplanning.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class TelegramWebhookRegistrationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramWebhookRegistrationService.class);

    private final TelegramProperties properties;
    private final String appEnvironment;
    private final RestClient restClient;

    public TelegramWebhookRegistrationService(TelegramProperties properties, Environment environment, RestClient.Builder builder) {
        this.properties = properties;
        this.appEnvironment = environment.getProperty("app.environment", "local");
        this.restClient = builder.baseUrl("https://api.telegram.org").build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartupWhenEnabled() {
        if (properties.webhookAutoRegister()) {
            registerWebhook();
        }
    }

    public void registerWebhook() {
        if (!properties.hasWebhookConfiguration(appEnvironment)) {
            LOGGER.warn("Telegram webhook registration skipped because configuration is incomplete.");
            return;
        }
        restClient.post()
                .uri("/bot{token}/setWebhook", properties.botToken(appEnvironment))
                .body(Map.of(
                        "url", properties.webhookUrl(appEnvironment),
                        "secret_token", properties.webhookSecret(appEnvironment),
                        "drop_pending_updates", false
                ))
                .retrieve()
                .toBodilessEntity();
        LOGGER.info("Telegram webhook registered environment={} bot={} url={}",
                appEnvironment, properties.botKind(appEnvironment), sanitizeUrl(properties.webhookUrl(appEnvironment)));
    }

    private String sanitizeUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("(?i)(bot)[0-9]+:[A-Za-z0-9_-]+", "$1<redacted>");
    }
}
