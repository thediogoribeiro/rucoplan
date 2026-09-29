package pt.rucodel.productionplanning.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.integrations.whatsapp")
public record WhatsAppProperties(
        boolean enabled,
        String accessToken,
        String appSecret,
        String verifyToken,
        String phoneNumberId,
        String businessAccountId,
        String graphApiVersion,
        String webhookPublicUrl
) {
    public String effectiveGraphApiVersion() {
        return graphApiVersion == null || graphApiVersion.isBlank() ? "v26.0" : graphApiVersion.trim();
    }

    public boolean hasRequiredWebhookConfiguration() {
        return notBlank(appSecret) && notBlank(verifyToken);
    }

    public boolean hasRequiredSendConfiguration() {
        return notBlank(accessToken) && notBlank(phoneNumberId);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
