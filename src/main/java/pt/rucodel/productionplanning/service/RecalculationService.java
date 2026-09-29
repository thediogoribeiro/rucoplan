package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.entity.ProductionSiteEntity;
import pt.rucodel.productionplanning.repository.ProductionPlanRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class RecalculationService {
    private final ProductionPlanRepository plans;
    private final Clock clock;
    private final ZoneId businessZone;
    private final ProductionSiteService productionSites;

    public RecalculationService(ProductionPlanRepository plans, Clock clock, AppProperties appProperties,
                                ProductionSiteService productionSites) {
        this.plans = plans;
        this.clock = clock;
        this.businessZone = ZoneId.of(appProperties.timezone());
        this.productionSites = productionSites;
    }

    @Transactional
    public void markCurrentAndFuturePlans() {
        markCurrentAndFuturePlans(ProductionSiteCode.PT);
    }

    @Transactional
    public void markCurrentAndFuturePlans(ProductionSiteEntity site) {
        if (site == null) {
            markCurrentAndFuturePlans();
            return;
        }
        plans.markCurrentPlansForRecalculationForSite(site.getCode(),
                LocalDate.now(clock.withZone(ZoneId.of(site.getTimezone()))));
    }

    @Transactional
    public void markCurrentAndFuturePlans(ProductionSiteCode siteCode) {
        ProductionSiteEntity site = productionSites.requireByCode(siteCode);
        plans.markCurrentPlansForRecalculationForSite(siteCode,
                LocalDate.now(clock.withZone(ZoneId.of(site.getTimezone()))));
    }
}
