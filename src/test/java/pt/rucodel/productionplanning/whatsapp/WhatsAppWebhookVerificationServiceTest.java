package pt.rucodel.productionplanning.whatsapp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppWebhookVerificationServiceTest {

    @Test
    void acceptsCurrentMetaWebhookHandshakeWhenTokenMatches() {
        WhatsAppWebhookVerificationService service = new WhatsAppWebhookVerificationService(properties(true));

        assertThat(service.canVerify("subscribe", "verify-token")).isTrue();
    }

    @Test
    void rejectsInvalidVerifyToken() {
        WhatsAppWebhookVerificationService service = new WhatsAppWebhookVerificationService(properties(true));

        assertThat(service.canVerify("subscribe", "wrong-token")).isFalse();
    }

    @Test
    void rejectsVerificationWhenIntegrationIsDisabled() {
        WhatsAppWebhookVerificationService service = new WhatsAppWebhookVerificationService(properties(false));

        assertThat(service.canVerify("subscribe", "verify-token")).isFalse();
    }

    private WhatsAppProperties properties(boolean enabled) {
        return new WhatsAppProperties(enabled, "", "app-secret", "verify-token",
                "phone-number-id", "waba-id", "v26.0", "");
    }
}
