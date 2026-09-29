package pt.rucodel.productionplanning.whatsapp;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppWebhookSignatureVerifierTest {

    @Test
    void validatesMetaSha256Signature() throws Exception {
        WhatsAppWebhookSignatureVerifier verifier = new WhatsAppWebhookSignatureVerifier(properties(true));
        String payload = "{\"object\":\"whatsapp_business_account\"}";

        assertThat(verifier.isValid(payload, signature(payload))).isTrue();
    }

    @Test
    void rejectsInvalidSignature() {
        WhatsAppWebhookSignatureVerifier verifier = new WhatsAppWebhookSignatureVerifier(properties(true));

        assertThat(verifier.isValid("{}", "sha256=invalid")).isFalse();
    }

    @Test
    void rejectsSignatureWhenIntegrationIsDisabled() throws Exception {
        WhatsAppWebhookSignatureVerifier verifier = new WhatsAppWebhookSignatureVerifier(properties(false));
        String payload = "{}";

        assertThat(verifier.isValid(payload, signature(payload))).isFalse();
    }

    private String signature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("app-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private WhatsAppProperties properties(boolean enabled) {
        return new WhatsAppProperties(enabled, "", "app-secret", "verify-token",
                "phone-number-id", "waba-id", "v26.0", "");
    }
}
