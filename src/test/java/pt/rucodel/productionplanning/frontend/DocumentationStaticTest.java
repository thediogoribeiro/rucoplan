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
        assertThat(openApi).contains("/driver/requests");
        assertThat(openApi).contains("wheelQuantities");
        assertThat(openApi).contains("BIPARTITE", "WASHED", "NORMAL");
    }

    @Test
    void readmeDocumentsStartupTestingPlanningAndIntegrations() throws Exception {
        String readme = Files.readString(Path.of("README.md"));

        assertThat(readme).contains("APP_DISPLAY_NAME");
        assertThat(readme).contains("./scripts/start-local-stack.sh");
        assertThat(readme).contains("07:00");
        assertThat(readme).contains("Target mínimo");
        assertThat(readme).contains("Target máximo");
        assertThat(readme).contains("Fecho do turno");
        assertThat(readme).contains("Bipartidas");
        assertThat(readme).contains("Lavadas");
        assertThat(readme).contains("Normais");
        assertThat(readme).contains("totalQuantity = bipartiteQuantity + washedQuantity + normalQuantity");
        assertThat(readme).contains("does not implement an external chat-provider conversation");
        assertThat(readme).contains("RucoPI");
        assertThat(readme).contains("RucoFi");
        assertThat(readme).contains("admin123");
    }
}
