package pt.rucodel.productionplanning.security.local;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import pt.rucodel.productionplanning.security.AuthenticationMode;

@Configuration
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "LOCAL", matchIfMissing = true)
public class LocalAuthenticationConfiguration {
    @Bean
    AuthenticationMode authenticationMode() {
        return AuthenticationMode.LOCAL;
    }

    @Bean
    UserDetailsService localUserDetailsService(LocalAuthenticationProperties properties) {
        return new InMemoryUserDetailsManager(User.withUsername(properties.username())
                .password("{noop}" + properties.password())
                .roles("ADMIN")
                .build());
    }
}
