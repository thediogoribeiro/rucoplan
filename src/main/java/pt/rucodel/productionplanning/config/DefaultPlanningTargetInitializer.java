package pt.rucodel.productionplanning.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.PlanningTargetDefaults;
import pt.rucodel.productionplanning.entity.ProductionTargetConfigurationEntity;
import pt.rucodel.productionplanning.repository.ProductionTargetConfigurationRepository;

@Component
public class DefaultPlanningTargetInitializer implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPlanningTargetInitializer.class);
    private static final long DEFAULT_TARGET_ADVISORY_LOCK = 7_492_730_011L;

    private final ProductionTargetConfigurationRepository targets;
    private final JdbcTemplate jdbcTemplate;

    public DefaultPlanningTargetInitializer(ProductionTargetConfigurationRepository targets, JdbcTemplate jdbcTemplate) {
        this.targets = targets;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        lockDefaultTargetInitialization();
        if (targets.count() > 0) {
            return;
        }
        ProductionTargetConfigurationEntity entity = new ProductionTargetConfigurationEntity();
        entity.setMinimumDailyTarget(PlanningTargetDefaults.MINIMUM_DAILY_TARGET);
        entity.setRegularDailyCapacity(PlanningTargetDefaults.REGULAR_DAILY_CAPACITY);
        entity.setEffectiveFrom(PlanningTargetDefaults.EFFECTIVE_FROM);
        entity.setCreatedBy(PlanningTargetDefaults.CREATED_BY);
        targets.saveAndFlush(entity);
        LOGGER.info("Created default production targets minimumDailyTarget={} regularDailyCapacity={}",
                PlanningTargetDefaults.MINIMUM_DAILY_TARGET,
                PlanningTargetDefaults.REGULAR_DAILY_CAPACITY);
    }

    private void lockDefaultTargetInitialization() {
        try {
            jdbcTemplate.execute("select pg_advisory_xact_lock(" + DEFAULT_TARGET_ADVISORY_LOCK + ")");
        } catch (DataAccessException ex) {
            LOGGER.debug("PostgreSQL advisory lock for default target initialization is not available in this environment.", ex);
        }
    }
}
