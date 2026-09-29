package pt.rucodel.productionplanning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pt.rucodel.productionplanning.domain.GenerationTrigger;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
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

    public WheelIntakePlanningRecalculationListener(WheelIntakeRequestRepository requests,
                                                    MultiDayProductionPlanningService productionPlanning,
                                                    Clock clock) {
        this.requests = requests;
        this.productionPlanning = productionPlanning;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recalculateAfterRequestCommit(WheelIntakeRequestCommunicatedEvent event) {
        recalculateAfterRequestCommit(event.requestId(), event.source(), event.productionSite(), "communicated");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recalculateAfterArrivalCommit(WheelIntakeRequestArrivedEvent event) {
        recalculateAfterRequestCommit(event.requestId(), event.source(), event.productionSite(), "arrival");
    }

    private void recalculateAfterRequestCommit(java.util.UUID requestId, RequestSource source,
                                               ProductionSiteCode eventSite, String reason) {
        try {
            requests.findById(requestId)
                    .ifPresent(request -> {
                        LocalDate date = affectedDate(request);
                        ProductionSiteCode site = request.getProductionSite() == null ? eventSite : request.getProductionSite().getCode();
                        LOGGER.info("Production plan recalculation scheduled after request commit site={} requestId={} requestCode={} source={} reason={} affectedDate={}",
                                site, request.getId(), request.getRequestCode(), source, reason, date);
                        productionPlanning.recalculate(site, date, GenerationTrigger.AUTOMATIC_RECALCULATION, "SYSTEM");
                        LOGGER.info("Production plan recalculation completed after request commit site={} requestId={} requestCode={} source={} reason={} affectedDate={}",
                                site, request.getId(), request.getRequestCode(), source, reason, date);
                    });
        } catch (RuntimeException exception) {
            LOGGER.warn("Production plan recalculation failed after request commit site={} requestId={} source={} reason={}",
                    eventSite, requestId, source, reason, exception);
        }
    }

    private LocalDate affectedDate(WheelIntakeRequestEntity request) {
        ZoneId zone = request.getProductionSite() == null
                ? ZoneId.of(ProductionSiteCode.PT.timezone())
                : ZoneId.of(request.getProductionSite().getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate availabilityDate = request.getExpectedFactoryDropOffWindowEnd()
                .atZoneSameInstant(zone)
                .toLocalDate();
        return availabilityDate.isBefore(today) ? today : availabilityDate;
    }
}
