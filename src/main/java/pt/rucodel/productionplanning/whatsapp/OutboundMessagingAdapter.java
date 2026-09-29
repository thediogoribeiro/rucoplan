package pt.rucodel.productionplanning.whatsapp;

public interface OutboundMessagingAdapter {
    String sendText(String recipientWaId, String message);
}
