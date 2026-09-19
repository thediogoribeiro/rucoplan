package pt.rucodel.productionplanning.frontend;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendStaticFlowTest {
    private static final Path STATIC = Path.of("src/main/resources/static");

    @Test
    void driverFrontendCoversLoginRequestCreationValidationAndIsolationEndpoints() throws Exception {
        String login = Files.readString(STATIC.resolve("js/login.js"));
        String driver = Files.readString(STATIC.resolve("js/driver.js"));
        String driverHtml = Files.readString(STATIC.resolve("driver.html"));

        assertThat(login).contains("/api/v1/auth/login");
        assertThat(driverHtml).contains("Motorista/Vendedor");
        assertThat(driver).contains("/api/v1/driver/requests");
        assertThat(driver).contains("Jantes bipartidas", "Jantes lavadas", "Jantes normais");
        assertThat(driver).contains("Total de jantes");
        assertThat(driver).contains("wheelQuantities");
        assertThat(driver).contains("Quando prevê deixar as jantes na fábrica?");
        assertThat(driver).contains("Quando pretende levantar as jantes prontas na fábrica?");
        assertThat(driver).contains("type=\"datetime-local\"");
        assertThat(driver).contains("/cancel");
    }

    @Test
    void administratorFrontendCoversSettingsArrivalPlanRiskOverflowAndPrinting() throws Exception {
        String admin = Files.readString(STATIC.resolve("js/admin.js"));
        String adminHtml = Files.readString(STATIC.resolve("admin.html"));
        String print = Files.readString(STATIC.resolve("js/print-plan.js"));

        assertThat(adminHtml).contains("Planeamento de Produção", "Fecho do turno", "Targets", "Plano diário", "Capacidade");
        assertThat(adminHtml).contains("nav-disabled", "Em construção");
        assertThat(adminHtml.indexOf("Auditoria")).isLessThan(adminHtml.indexOf("Targets"));
        assertThat(admin).contains("Capacidade está temporariamente em construção");
        assertThat(admin).contains("/api/v1/admin/messaging-identities");
        assertThat(admin).contains("/api/v1/admin/customer-registration-requests");
        assertThat(admin).contains("Identidades Telegram e canais de comunicação");
        assertThat(admin).contains("Clientes pendentes");
        assertThat(admin).contains("Bloquear", "Reativar", "Associar");
        assertThat(admin).contains("/api/v1/admin/production-plans");
        assertThat(admin).contains("/api/v1/admin/planning-targets");
        assertThat(admin).contains("Target mínimo diário");
        assertThat(admin).contains("Target máximo diário — capacidade regular");
        assertThat(admin).contains("Marcar tudo como concluído");
        assertThat(admin).contains("Tipos de jantes");
        assertThat(admin).contains("closure-card", "closure-type-table");
        assertThat(admin).contains("Concluído");
        assertThat(admin).contains("Pendente");
        assertThat(admin).contains("Aviso de fecho pendente");
        assertThat(admin).contains("Horas extra");
        assertThat(admin).contains("Excesso estimado");
        assertThat(admin).contains("Dia anterior", "Amanhã", "Dia seguinte");
        assertThat(admin).contains("Existem targets mais recentes");
        assertThat(admin).contains("remainingQuantity");
        assertThat(admin).contains("/api/v1/admin/requests");
        assertThat(admin).contains("/api/v1/admin/production-plans/${state.date}/recalculate");
        assertThat(admin).contains("/api/v1/admin/dashboard/stream");
        assertThat(admin).contains("requestCode", "driverCode", "customerCode", "ID RucoFi");
        assertThat(admin).contains("wheelSummaryVertical", "factoryWindow");
        assertThat(admin).doesNotContain("Reconhecer");
        assertThat(admin).contains("OVER_CAPACITY");
        assertThat(admin).contains("AT_RISK");
        assertThat(admin).contains("actualReceivedWheelQuantity");
        assertThat(print).contains("/api/v1/admin/plans");
        assertThat(print).contains("window.print");
    }

    @Test
    void apiClientDisplaysErrorsAndUsesBearerAuthentication() throws Exception {
        String api = Files.readString(STATIC.resolve("js/api.js"));

        assertThat(api).contains("Authorization");
        assertThat(api).contains("Bearer");
        assertThat(api).contains("payload.message");
        assertThat(api).contains("details");
    }
}
