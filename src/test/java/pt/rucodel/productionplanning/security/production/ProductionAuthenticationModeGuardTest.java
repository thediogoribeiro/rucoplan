package pt.rucodel.productionplanning.security.production;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import pt.rucodel.productionplanning.security.local.LocalAuthenticationProvider;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ProductionAuthenticationModeGuardTest {
    @Test
    void productionRequiresDatabaseAuthenticationMode() {
        assertThatThrownBy(() -> guard("LOCAL", mock(DatabaseAuthenticationProvider.class),
                mock(DatabaseUserDetailsService.class), null).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_AUTH_MODE=DATABASE");
    }

    @Test
    void databaseModeIsAcceptedInProduction() {
        assertThatCode(() -> guard("DATABASE", mock(DatabaseAuthenticationProvider.class),
                mock(DatabaseUserDetailsService.class), null).afterSingletonsInstantiated())
                .doesNotThrowAnyException();
    }

    @Test
    void databaseModeRequiresDatabaseProvider() {
        assertThatThrownBy(() -> guard("DATABASE", null, mock(DatabaseUserDetailsService.class), null)
                .afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DatabaseAuthenticationProvider");
    }

    @Test
    void productionRejectsLocalProvider() {
        assertThatThrownBy(() -> guard("DATABASE", mock(DatabaseAuthenticationProvider.class),
                mock(DatabaseUserDetailsService.class), mock(LocalAuthenticationProvider.class))
                .afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LocalAuthenticationProvider");
    }

    private ProductionAuthenticationModeGuard guard(String authMode,
                                                    DatabaseAuthenticationProvider databaseProvider,
                                                    DatabaseUserDetailsService databaseUserDetailsService,
                                                    LocalAuthenticationProvider localProvider) {
        return new ProductionAuthenticationModeGuard(authMode, provider(databaseProvider),
                provider(databaseUserDetailsService), provider(localProvider));
    }

    private <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }
        };
    }
}
