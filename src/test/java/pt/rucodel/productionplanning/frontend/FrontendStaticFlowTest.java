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
        assertThat(login).contains("const payload = {");
        assertThat(login).contains("username: form.username.value.trim()");
        assertThat(login).contains("password: form.password.value");
        assertThat(login).contains("productionSite: form.productionSite.value");
        assertThat(login).contains("api.postJson('/api/v1/auth/login', payload, { auth: false })");
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

        String nav = adminHtml.substring(adminHtml.indexOf("<nav class=\"nav\""), adminHtml.indexOf("</nav>"));
        assertThat(nav).contains("Plano Diário", "Entrada na Fábrica", "Fecho do Turno", "Planeamento de Produção", "Targets", "Novo Pedido", "Pedidos", "Motoristas", "Clientes", "Definições");
        assertThat(nav.indexOf("Plano Diário")).isLessThan(nav.indexOf("Entrada na Fábrica"));
        assertThat(nav.indexOf("Entrada na Fábrica")).isLessThan(nav.indexOf("Fecho do Turno"));
        assertThat(nav.indexOf("Fecho do Turno")).isLessThan(nav.indexOf("Planeamento de Produção"));
        assertThat(nav.indexOf("Planeamento de Produção")).isLessThan(nav.indexOf("Targets"));
        assertThat(nav.indexOf("Targets")).isLessThan(nav.indexOf("Novo Pedido"));
        assertThat(nav.indexOf("Novo Pedido")).isLessThan(nav.indexOf("Pedidos"));
        assertThat(nav.indexOf("Pedidos")).isLessThan(nav.indexOf("Motoristas"));
        assertThat(nav.indexOf("Motoristas")).isLessThan(nav.indexOf("Clientes"));
        assertThat(nav.indexOf("Clientes")).isLessThan(nav.indexOf("Definições"));
        assertThat(nav.trim()).endsWith("<a href=\"#settings/diagnostics\">Definições</a>");
        assertThat(nav).doesNotContain("Diagnóstico do sistema", "Auditoria", "Capacidade", "nav-disabled");
        assertThat(admin).contains("settings/diagnostics", "settings/audit", "settings/capacity");
        assertThat(admin).contains("renderNewRequest", "Novo Pedido", "/api/v1/admin/requests/manual");
        assertThat(admin).contains("Motorista, se aplicável", "Sem motorista", "As jantes já estão na fábrica?");
        assertThat(admin).contains("MANUAL", "WhatsApp", "sourceLabel");
        assertThat(admin).contains("diagnostics: 'settings/diagnostics'", "audit: 'settings/audit'", "capacity: 'settings/capacity'");
        assertThat(admin).contains("Definições &gt;", "settings-subnav");
        assertThat(admin).contains("Diagnóstico do Sistema", "Auditoria", "Capacidade");
        assertThat(admin).contains("Em construção");
        assertThat(admin).contains("A configuração de capacidade está temporariamente desativada");
        assertThat(admin).contains("não permite navegação nem edição operacional");
        assertThat(admin).contains("/api/v1/admin/messaging-identities");
        assertThat(admin).contains("/api/v1/admin/customer-registration-requests");
        assertThat(admin).contains("Identidades Telegram e canais de comunicação");
        assertThat(admin).contains("Clientes pendentes");
        assertThat(admin).contains("Bloquear", "Reativar", "Associar");
        assertThat(admin).contains("/api/v1/admin/production-plans");
        assertThat(admin).contains("/api/v1/admin/planning-targets");
        assertThat(admin).doesNotContain("Objetivo diário", "Capacidade diária");
        assertThat(admin).contains("Target mínimo");
        assertThat(admin).contains("Target máximo");
        assertThat(admin).contains("minimumTargetSnapshot", "maximumTargetSnapshot");
        assertThat(admin).contains("Excesso acima do target máximo");
        assertThat(admin).contains("Marcar tudo como concluído");
        assertThat(admin).contains("Tipos de jantes");
        assertThat(admin).contains("closure-card", "closure-type-table");
        assertThat(admin).contains("Fechar no turno", "/api/v1/admin/production-plans/${state.date}/items/${id}/close");
        assertThat(admin).contains("Indique pelo menos uma jante concluída para poder fechar este trabalho no turno.");
        assertThat(admin).contains("Não é possível fechar: nenhuma jante foi marcada como concluída.");
        assertThat(admin).contains("disabled aria-disabled=\"true\"");
        assertThat(admin).contains("Confirmar conclusão total", "Confirmar conclusão parcial");
        assertThat(admin).contains("Confirmar conclusão", "Confirmar fecho parcial");
        assertThat(admin).contains("Pedido fechado neste turno", "Fecho parcial registado");
        assertThat(admin).contains("data-close-confirmation", "data-close-help");
        assertThat(admin).contains("Concluído");
        assertThat(admin).contains("Pendente");
        assertThat(admin).contains("Pedidos por fechar", "Fechados neste turno");
        assertThat(admin).contains("Aviso de fecho pendente");
        assertThat(admin).contains("Horas extra necessárias");
        assertThat(admin).contains("jantes acima do target máximo", "Plano dentro do target máximo");
        assertThat(admin).contains("metric overtime", "'yes' : 'no'");
        assertThat(admin).contains("Dia anterior", "Amanhã", "Dia seguinte", "Atualizar");
        assertThat(admin).contains("Existem targets mais recentes");
        assertThat(admin).contains("remainingQuantity");
        assertThat(admin).contains("/api/v1/admin/requests");
        assertThat(admin).contains("productionDate", "Data do plano de produção", "Correção de fecho");
        assertThat(admin).contains("canReopenClosure", "reconciliationId", "closureStatus", "reopenBlockReason");
        assertThat(admin).contains("Reabrir no Fecho do Turno", "Reabertura indisponível");
        assertThat(admin).contains("Reabrir este pedido no Fecho do Turno?", "Motivo da reabertura", "Confirmar reabertura");
        assertThat(admin).contains("O motivo da reabertura é obrigatório.");
        assertThat(admin).contains("highlightedClosureLineId", "highlighted ? 'highlighted'");
        assertThat(admin).contains("/api/v1/admin/production-plans/${request.productionDate}/items/${request.productionPlanItemId}/reopen");
        assertThat(admin).contains("productionLineVersion");
        assertThat(admin).contains("/api/v1/admin/factory-arrivals?status=COMMUNICATED");
        assertThat(admin).contains("Confirmar chegada");
        assertThat(admin).contains("Chegada por confirmar", "requestLifecycleStatus");
        assertThat(admin).doesNotContain("data-status");
        assertThat(admin).doesNotContain("/api/v1/admin/requests/${id}/status");
        assertThat(admin).contains("/api/v1/admin/production-plans/${state.date}/recalculate");
        assertThat(admin).doesNotContain("/api/v1/admin/production-plans/recalculate");
        assertThat(admin).contains("loadPlanningData()");
        assertThat(admin).contains("const refreshed = await loadPlanningData()");
        assertThat(admin).doesNotContain("const [productionPlan, productionPlans, planningTargets] = await Promise.all");
        assertThat(admin).contains("state.targetsError");
        assertThat(admin).contains("planning.confirmedRequestsErrorCode", "planning.openPlansErrorCode", "planning.planLinesErrorCode");
        assertThat(admin).contains("/api/v1/admin/dashboard/stream");
        assertThat(admin).contains("/api/v1/auth/stream-session");
        assertThat(admin).doesNotContain("access_token");
        assertThat(admin).doesNotContain("http://localhost:8082");
        assertThat(admin).doesNotContain("https://localhost:8082");
        assertThat(admin).contains("dashboard-connected", "production-plan-updated", "document.visibilityState === 'visible'");
        assertThat(admin).contains("REST fallback errors are handled by the normal screen actions");
        assertThat(admin).contains("refreshAdminData()");
        assertThat(admin).contains("requestCode", "driverCode", "customerCode", "ID RucoFi");
        assertThat(admin).contains("Ligação em tempo real indisponível", "Tentar novamente");
        assertThat(admin).contains("Ver detalhes técnicos", "/admin/system-diagnostics/realtime#settings/diagnostics");
        assertThat(admin).contains("Não existem dados de planeamento para apresentar");
        assertThat(admin).contains("Não foi possível estabelecer ligação à base de dados");
        assertThat(admin).contains("Não foi possível contactar o servidor do RucoPlan");
        assertThat(admin).contains("Não foi possível gerar o plano de produção", "Correlation ID", "retry-planning-operation");
        assertThat(admin).contains("const lines = plan?.lines || []");
        assertThat(admin).doesNotContain("alert(");
        assertThat(admin).contains("Copiar resumo de diagnóstico", "Database status", "Realtime status");
        assertThat(admin).contains("Configuração do webhook", "Último webhook recebido", "Cloud API");
        assertThat(admin).contains("Indisponível", "Não foi possível verificar porque o backend está inacessível", "diagnosticCount");
        assertThat(admin).doesNotContain("Código externo");
        assertThat(admin).contains("wheelSummaryVertical", "factoryWindow");
        assertThat(admin).contains("formatLocalDateCompact", "day: '2-digit'", "month: '2-digit'", "Horário não definido", "madrugada");
        assertThat(admin).doesNotContain("month: 'long'");
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
        assertThat(api).contains("payload?.code", "payload?.correlationId", "BACKEND_UNAVAILABLE");
        assertThat(api).contains("X-Correlation-ID", "credentials", "application/problem+json");
        assertThat(api).contains("fetch(path");
        assertThat(api).contains("response.status === 204");
        assertThat(api).contains("details");
        assertThat(api).doesNotContain("localhost:8082");
    }

    @Test
    void staticAdminAssetsAreConfiguredForBrowserRevalidation() throws Exception {
        String cacheConfig = Files.readString(Path.of("src/main/java/pt/rucodel/productionplanning/config/StaticResourceCacheConfig.java"));

        assertThat(cacheConfig).contains("/*.html", "/js/**", "/css/**");
        assertThat(cacheConfig).contains("maxAge(0", "mustRevalidate");
    }
}
