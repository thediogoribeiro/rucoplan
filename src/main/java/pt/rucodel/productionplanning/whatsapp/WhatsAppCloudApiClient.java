package pt.rucodel.productionplanning.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class WhatsAppCloudApiClient implements OutboundMessagingAdapter {
    private final WhatsAppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public WhatsAppCloudApiClient(WhatsAppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String sendText(String recipientWaId, String message) {
        if (!properties.enabled() || !properties.hasRequiredSendConfiguration()) {
            return null;
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "messaging_product", "whatsapp",
                    "to", recipientWaId,
                    "type", "text",
                    "text", Map.of("preview_url", false, "body", message)
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://graph.facebook.com/%s/%s/messages"
                            .formatted(properties.effectiveGraphApiVersion(), properties.phoneNumberId())))
                    .timeout(Duration.ofSeconds(10))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("WhatsApp Cloud API returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("WhatsApp Cloud API send interrupted.", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("WhatsApp Cloud API send failed.", ex);
        }
    }
}
