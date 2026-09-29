package pt.rucodel.productionplanning.security.production;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.rucodel.productionplanning.security.AuthenticationMode;

@Configuration
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "DATABASE")
public class DatabaseAuthenticationConfiguration {
    @Bean
    AuthenticationMode authenticationMode() {
        return AuthenticationMode.DATABASE;
    }
}
