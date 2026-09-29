package pt.rucodel.productionplanning.whatsapp;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class WhatsAppWebhookVerificationService {
    private final WhatsAppProperties properties;

    public WhatsAppWebhookVerificationService(WhatsAppProperties properties) {
        this.properties = properties;
    }

    public boolean canVerify(String mode, String verifyToken) {
        if (!properties.enabled() || properties.verifyToken() == null || properties.verifyToken().isBlank()) {
            return false;
        }
        return "subscribe".equals(mode)
                && verifyToken != null
                && MessageDigest.isEqual(properties.verifyToken().getBytes(StandardCharsets.UTF_8),
                verifyToken.getBytes(StandardCharsets.UTF_8));
    }
}
