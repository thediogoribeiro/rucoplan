package pt.rucodel.productionplanning.whatsapp;

import java.util.List;

public interface InboundMessagingAdapter {
    List<ConversationMessage> map(String rawPayload);
}
