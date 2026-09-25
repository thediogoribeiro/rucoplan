package pt.rucodel.productionplanning.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import pt.rucodel.productionplanning.dto.ErrorResponse;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, BearerTokenAuthenticationFilter bearerFilter,
                                            ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeSecurityError(
                                objectMapper,
                                request.getRequestURI(),
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "AUTHENTICATION_REQUIRED",
                                "É necessário iniciar sessão para aceder a este recurso."))
                        .accessDeniedHandler((request, response, exception) -> writeSecurityError(
                                objectMapper,
                                request.getRequestURI(),
                                response,
                                HttpStatus.FORBIDDEN,
                                "ACCESS_DENIED",
                                "Não tem permissões para aceder a este recurso.")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/login.html", "/driver.html", "/admin.html", "/print-plan.html",
                                "/admin/system-diagnostics/realtime",
                                "/css/**", "/js/**", "/openapi.yaml").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/integrations/whatsapp/requests").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/integrations/telegram/webhook").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/driver/**").hasRole("DRIVER")
                        .requestMatchers("/api/v1/auth/me", "/api/v1/customers/search").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private void writeSecurityError(ObjectMapper objectMapper, String path,
                                    jakarta.servlet.http.HttpServletResponse response,
                                    HttpStatus status, String code, String message) throws java.io.IOException {
        String correlationId = UUID.randomUUID().toString();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("X-Correlation-ID", correlationId);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.problem(
                status,
                code,
                message,
                path,
                List.of(),
                correlationId
        ));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Use bearer-token authentication.");
        };
    }
}
