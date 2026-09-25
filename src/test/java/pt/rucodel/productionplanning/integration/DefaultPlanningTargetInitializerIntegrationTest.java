package pt.rucodel.productionplanning.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pt.rucodel.productionplanning.config.DefaultPlanningTargetInitializer;
import pt.rucodel.productionplanning.domain.PlanningTargetDefaults;
import pt.rucodel.productionplanning.entity.ProductionTargetConfigurationEntity;
import pt.rucodel.productionplanning.repository.ProductionTargetConfigurationRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DefaultPlanningTargetInitializerIntegrationTest {
    @jakarta.annotation.Resource DefaultPlanningTargetInitializer initializer;
    @jakarta.annotation.Resource ProductionTargetConfigurationRepository targetConfigurations;

    @BeforeEach
    void setUp() {
        targetConfigurations.deleteAll();
    }

    @Test
    void createsDefaultTargetsOnceAndDoesNotOverwriteExistingTargets() {
        initializer.run(null);

        assertThat(targetConfigurations.findAll()).singleElement().satisfies(target -> {
            assertThat(target.getMinimumDailyTarget()).isEqualTo(PlanningTargetDefaults.MINIMUM_DAILY_TARGET);
            assertThat(target.getRegularDailyCapacity()).isEqualTo(PlanningTargetDefaults.REGULAR_DAILY_CAPACITY);
            assertThat(target.getCreatedBy()).isEqualTo(PlanningTargetDefaults.CREATED_BY);
        });

        initializer.run(null);
        assertThat(targetConfigurations.findAll()).hasSize(1);

        ProductionTargetConfigurationEntity existing = targetConfigurations.findAll().getFirst();
        existing.setMinimumDailyTarget(20);
        existing.setRegularDailyCapacity(30);
        targetConfigurations.saveAndFlush(existing);

        initializer.run(null);

        assertThat(targetConfigurations.findAll()).singleElement().satisfies(target -> {
            assertThat(target.getMinimumDailyTarget()).isEqualTo(20);
            assertThat(target.getRegularDailyCapacity()).isEqualTo(30);
        });
    }
}
