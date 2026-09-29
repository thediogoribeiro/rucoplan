package pt.rucodel.productionplanning.security.production;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("production")
public class ProductionAuthenticationModeGuard implements SmartInitializingSingleton {
    private final String authMode;

    public ProductionAuthenticationModeGuard(@Value("${app.auth.mode:LOCAL}") String authMode) {
        this.authMode = authMode;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!"DATABASE".equalsIgnoreCase(authMode)) {
            throw new IllegalStateException("Production profile requires APP_AUTH_MODE=DATABASE.");
        }
    }
}
