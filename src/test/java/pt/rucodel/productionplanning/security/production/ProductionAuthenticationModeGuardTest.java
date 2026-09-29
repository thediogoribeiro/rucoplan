package pt.rucodel.productionplanning.security.production;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionAuthenticationModeGuardTest {
    @Test
    void productionRequiresDatabaseAuthenticationMode() {
        assertThatThrownBy(() -> new ProductionAuthenticationModeGuard("LOCAL").afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_AUTH_MODE=DATABASE");
    }

    @Test
    void databaseModeIsAcceptedInProduction() {
        assertThatCode(() -> new ProductionAuthenticationModeGuard("DATABASE").afterSingletonsInstantiated())
                .doesNotThrowAnyException();
    }
}
