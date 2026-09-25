package pt.rucodel.productionplanning.domain;

import java.util.UUID;

public record WheelIntakeRequestArrivedEvent(UUID requestId, RequestSource source) {
}
