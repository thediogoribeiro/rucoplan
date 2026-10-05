package pt.rucodel.productionplanning.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("production")
public class ProductionRuntimeDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductionRuntimeDiagnostics.class);

    @Bean
    CommandLineRunner productionRuntimeDiagnosticsRunner(
            PasswordEncoder passwordEncoder,
            UserDetailsService userDetailsService,
            @Value("${app.auth.mode:}") String authMode,
            @Value("${spring.datasource.url:}") String datasourceUrl
    ) {
        return args -> LOGGER.info(
                "production.runtime authMode={} passwordEncoder={} userDetailsService={} datasource={}",
                authMode,
                passwordEncoder.getClass().getSimpleName(),
                userDetailsService.getClass().getSimpleName(),
                sanitizeJdbcUrl(datasourceUrl)
        );
    }

    static String sanitizeJdbcUrl(String value) {
        if (value == null || value.isBlank()) {
            return "unconfigured";
        }
        return value
                .replaceAll("(?i)(password=)[^&;]+", "$1<redacted>")
                .replaceAll("(?i)(user=)[^&;]+", "$1<redacted>");
    }
}
