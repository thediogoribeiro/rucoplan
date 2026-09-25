package pt.rucodel.productionplanning.domain;

import java.util.UUID;

public record WheelIntakeRequestCommunicatedEvent(UUID requestId, RequestSource source) {
}
