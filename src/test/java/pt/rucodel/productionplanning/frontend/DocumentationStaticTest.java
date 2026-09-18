package pt.rucodel.productionplanning.frontend;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentationStaticTest {
    @Test
    void openApiDocumentsWhatsappExampleAndCoreEndpoints() throws Exception {
        String openApi = Files.readString(Path.of("src/main/resources/static/openapi.yaml"));

        assertThat(openApi).contains("/integrations/whatsapp/requests");
        assertThat(openApi).contains("externalMessageId: unique-message-id");
        assertThat(openApi).contains("/admin/plans/generate");
        assertThat(openApi).contains("/admin/production-plans");
        assertThat(openApi).contains("/admin/planning-targets");
        assertThat(openApi).contains("/admin/settings/daily");
        assertThat(openApi).contains("/admin/messaging-identities");
        assertThat(openApi).contains("/driver/requests");
        assertThat(openApi).contains("wheelQuantities");
        assertThat(openApi).contains("BIPARTITE", "WASHED", "NORMAL");
    }

    @Test
    void readmeDocumentsStartupTestingPlanningAndIntegrations() throws Exception {
        String readme = Files.readString(Path.of("README.md"));

        assertThat(readme).contains("Rucoplan");
        assertThat(readme).contains("Wheel Types");
        assertThat(readme).contains("Planning");
        assertThat(readme).contains("Shift Closure");
        assertThat(readme).contains("Telegram account");
        assertThat(readme).contains("not the physical device");
        assertThat(readme).contains("Telegram Bot API");
        assertThat(readme).contains("mvn test");
    }
}
