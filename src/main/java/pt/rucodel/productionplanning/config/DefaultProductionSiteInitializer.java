package pt.rucodel.productionplanning.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.entity.ProductionSiteEntity;
import pt.rucodel.productionplanning.repository.ProductionSiteRepository;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DefaultProductionSiteInitializer implements ApplicationRunner {
    private final ProductionSiteRepository sites;

    public DefaultProductionSiteInitializer(ProductionSiteRepository sites) {
        this.sites = sites;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensure(ProductionSiteCode.PT);
        ensure(ProductionSiteCode.LUX);
    }

    private void ensure(ProductionSiteCode code) {
        ProductionSiteEntity site = sites.findByCode(code).orElseGet(ProductionSiteEntity::new);
        site.setCode(code);
        site.setDisplayName(code.displayName());
        site.setTimezone(code.timezone());
        site.setActive(true);
        site.setCreatedBy("SYSTEM");
        site.setUpdatedBy("SYSTEM");
        sites.save(site);
    }
}
