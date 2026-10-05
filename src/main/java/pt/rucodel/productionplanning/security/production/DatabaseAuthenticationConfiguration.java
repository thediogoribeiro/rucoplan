package pt.rucodel.productionplanning.security.production;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import pt.rucodel.productionplanning.security.AuthenticationMode;

@Configuration
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "DATABASE")
public class DatabaseAuthenticationConfiguration {
    @Bean
    AuthenticationMode authenticationMode() {
        return AuthenticationMode.DATABASE;
    }

    @Bean
    AuthenticationManager databaseAuthenticationManager(DatabaseUserDetailsService userDetailsService,
                                                        PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
