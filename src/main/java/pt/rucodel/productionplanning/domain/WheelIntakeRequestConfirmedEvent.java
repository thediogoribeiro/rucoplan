package pt.rucodel.productionplanning.domain;

import java.util.UUID;

public record WheelIntakeRequestConfirmedEvent(UUID requestId, RequestSource source) {
}
