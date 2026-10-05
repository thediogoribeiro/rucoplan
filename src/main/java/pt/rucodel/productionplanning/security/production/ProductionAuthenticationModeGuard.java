package pt.rucodel.productionplanning.security.production;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pt.rucodel.productionplanning.security.local.LocalAuthenticationProvider;

@Component
@Profile("production")
public class ProductionAuthenticationModeGuard implements SmartInitializingSingleton {
    private final String authMode;
    private final ObjectProvider<DatabaseAuthenticationProvider> databaseProvider;
    private final ObjectProvider<DatabaseUserDetailsService> databaseUserDetailsService;
    private final ObjectProvider<LocalAuthenticationProvider> localProvider;

    public ProductionAuthenticationModeGuard(@Value("${app.auth.mode:LOCAL}") String authMode,
                                             ObjectProvider<DatabaseAuthenticationProvider> databaseProvider,
                                             ObjectProvider<DatabaseUserDetailsService> databaseUserDetailsService,
                                             ObjectProvider<LocalAuthenticationProvider> localProvider) {
        this.authMode = authMode;
        this.databaseProvider = databaseProvider;
        this.databaseUserDetailsService = databaseUserDetailsService;
        this.localProvider = localProvider;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!"DATABASE".equalsIgnoreCase(authMode)) {
            throw new IllegalStateException("Production profile requires APP_AUTH_MODE=DATABASE.");
        }
        if (databaseProvider.getIfAvailable() == null) {
            throw new IllegalStateException("Production profile requires DatabaseAuthenticationProvider.");
        }
        if (databaseUserDetailsService.getIfAvailable() == null) {
            throw new IllegalStateException("Production profile requires DatabaseUserDetailsService.");
        }
        if (localProvider.getIfAvailable() != null) {
            throw new IllegalStateException("Production profile must not create LocalAuthenticationProvider.");
        }
    }
}
