package pt.rucodel.productionplanning.whatsapp;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class WhatsAppWebhookSignatureVerifier {
    private final WhatsAppProperties properties;

    public WhatsAppWebhookSignatureVerifier(WhatsAppProperties properties) {
        this.properties = properties;
    }

    public boolean isValid(String rawBody, String suppliedSignature) {
        if (!properties.enabled()) {
            return false;
        }
        if (properties.appSecret() == null || properties.appSecret().isBlank()) {
            return false;
        }
        if (suppliedSignature == null || suppliedSignature.isBlank() || !suppliedSignature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.appSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    suppliedSignature.trim().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }
}
