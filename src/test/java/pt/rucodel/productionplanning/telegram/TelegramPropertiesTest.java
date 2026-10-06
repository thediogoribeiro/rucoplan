package pt.rucodel.productionplanning.telegram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramPropertiesTest {

    @Test
    void localUsesTestBotOnly() {
        TelegramProperties properties = properties("test-token", "test-bot", "test-secret", "https://local.test",
                "prod-token", "prod-bot", "prod-secret", "https://prod.test");

        assertThat(properties.botKind("local")).isEqualTo("test");
        assertThat(properties.botToken("local")).isEqualTo("test-token");
        assertThat(properties.botUsername("local")).isEqualTo("test-bot");
        assertThat(properties.webhookSecret("local")).isEqualTo("test-secret");
        assertThat(properties.webhookUrl("local")).isEqualTo("https://local.test/api/v1/integrations/telegram/webhook");
    }

    @Test
    void productionUsesProductionBotOnly() {
        TelegramProperties properties = properties("test-token", "test-bot", "test-secret", "https://local.test",
                "prod-token", "prod-bot", "prod-secret", "https://prod.test");

        assertThat(properties.botKind("production")).isEqualTo("production");
        assertThat(properties.botToken("production")).isEqualTo("prod-token");
        assertThat(properties.botUsername("production")).isEqualTo("prod-bot");
        assertThat(properties.webhookSecret("production")).isEqualTo("prod-secret");
        assertThat(properties.webhookUrl("production")).isEqualTo("https://prod.test/api/v1/integrations/telegram/webhook");
    }

    @Test
    void productionDoesNotFallbackToTestBot() {
        TelegramProperties properties = properties("test-token", "test-bot", "test-secret", "https://local.test",
                "", "prod-bot", "prod-secret", "https://prod.test");

        assertThatThrownBy(() -> properties.validateActiveEnvironment("production"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("production bot token is required");
    }

    @Test
    void localDoesNotFallbackToProductionBot() {
        TelegramProperties properties = properties("", "test-bot", "test-secret", "https://local.test",
                "prod-token", "prod-bot", "prod-secret", "https://prod.test");

        assertThatThrownBy(() -> properties.validateActiveEnvironment("local"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test bot token is required");
    }

    @Test
    void disabledTelegramDoesNotRequireBotSecrets() {
        TelegramProperties properties = new TelegramProperties(false, false,
                "", "", "", "", "", "", "", "");

        properties.validateActiveEnvironment("production");
    }

    private TelegramProperties properties(String testToken, String testUsername, String testSecret, String testUrl,
                                          String productionToken, String productionUsername, String productionSecret,
                                          String productionUrl) {
        return new TelegramProperties(true, false,
                testToken, testUsername, testSecret, testUrl,
                productionToken, productionUsername, productionSecret, productionUrl);
    }
}
