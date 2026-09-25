package pt.rucodel.productionplanning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pt.rucodel.productionplanning.domain.GenerationTrigger;
import pt.rucodel.productionplanning.domain.RequestSource;
import pt.rucodel.productionplanning.domain.WheelIntakeRequestArrivedEvent;
import pt.rucodel.productionplanning.domain.WheelIntakeRequestCommunicatedEvent;
import pt.rucodel.productionplanning.entity.WheelIntakeRequestEntity;
import pt.rucodel.productionplanning.repository.WheelIntakeRequestRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class WheelIntakePlanningRecalculationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(WheelIntakePlanningRecalculationListener.class);

    private final WheelIntakeRequestRepository requests;
    private final MultiDayProductionPlanningService productionPlanning;
    private final Clock clock;
    private final ZoneId businessZone;

    public WheelIntakePlanningRecalculationListener(WheelIntakeRequestRepository requests,
                                                    MultiDayProductionPlanningService productionPlanning,
                                                    Clock clock,
                                                    AppProperties appProperties) {
        this.requests = requests;
        this.productionPlanning = productionPlanning;
        this.clock = clock;
        this.businessZone = ZoneId.of(appProperties.timezone());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recalculateAfterRequestCommit(WheelIntakeRequestCommunicatedEvent event) {
        recalculateAfterRequestCommit(event.requestId(), event.source(), "communicated");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recalculateAfterArrivalCommit(WheelIntakeRequestArrivedEvent event) {
        recalculateAfterRequestCommit(event.requestId(), event.source(), "arrival");
    }

    private void recalculateAfterRequestCommit(java.util.UUID requestId, RequestSource source, String reason) {
        try {
            requests.findById(requestId)
                    .ifPresent(request -> {
                        LocalDate date = affectedDate(request);
                        LOGGER.info("Production plan recalculation scheduled after request commit requestId={} requestCode={} source={} reason={} affectedDate={}",
                                request.getId(), request.getRequestCode(), source, reason, date);
                        productionPlanning.recalculate(date, GenerationTrigger.AUTOMATIC_RECALCULATION, "SYSTEM");
                        LOGGER.info("Production plan recalculation completed after request commit requestId={} requestCode={} source={} reason={} affectedDate={}",
                                request.getId(), request.getRequestCode(), source, reason, date);
                    });
        } catch (RuntimeException exception) {
            LOGGER.warn("Production plan recalculation failed after request commit requestId={} source={} reason={}",
                    requestId, source, reason, exception);
        }
    }

    private LocalDate affectedDate(WheelIntakeRequestEntity request) {
        LocalDate today = LocalDate.now(clock.withZone(businessZone));
        LocalDate availabilityDate = request.getExpectedFactoryDropOffWindowEnd()
                .atZoneSameInstant(businessZone)
                .toLocalDate();
        return availabilityDate.isBefore(today) ? today : availabilityDate;
    }
}
