package pt.rucodel.productionplanning.whatsapp;

import org.springframework.stereotype.Component;

@Component
public class WhatsAppOutboundMessageAdapter {
    private final WhatsAppCloudApiClient client;

    public WhatsAppOutboundMessageAdapter(WhatsAppCloudApiClient client) {
        this.client = client;
    }

    public String sendText(String recipientWaId, String message) {
        return client.sendText(recipientWaId, message);
    }
}
