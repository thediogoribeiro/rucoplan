package pt.rucodel.productionplanning.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningCapacityIsolationStaticTest {
    @Test
    void multiDayPlannerDoesNotCallCapacityServices() throws Exception {
        String source = Files.readString(Path.of("src/main/java/pt/rucodel/productionplanning/service/MultiDayProductionPlanningService.java"));

        assertThat(source).doesNotContain("CapacityAlertService");
        assertThat(source).doesNotContain("CapacityAlertRepository");
        assertThat(source).doesNotContain("DailyProductionSettings");
        assertThat(source).doesNotContain("ProductionSettingsService");
        assertThat(source).doesNotContain("upsertFromPlan");
        assertThat(source).doesNotContain("recalculateFromToday");
    }
}
