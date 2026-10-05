package pt.rucodel.productionplanning.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.domain.UserRole;
import pt.rucodel.productionplanning.entity.ApplicationUserEntity;
import pt.rucodel.productionplanning.repository.ApplicationUserRepository;
import pt.rucodel.productionplanning.repository.ApplicationUserSiteRepository;
import pt.rucodel.productionplanning.security.AuthenticationProvider;
import pt.rucodel.productionplanning.security.production.DatabaseAuthenticationProvider;
import pt.rucodel.productionplanning.security.production.DatabaseUserDetailsService;
import pt.rucodel.productionplanning.dto.LoginRequest;
import pt.rucodel.productionplanning.service.AuthService;
import pt.rucodel.productionplanning.service.ProductionSiteService;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.auth.mode=DATABASE")
@ExtendWith(OutputCaptureExtension.class)
class DatabaseAuthenticationIntegrationTest {
    private static final String TEST_PASSWORD = "correct-test-password";
    private static final String WRONG_TEST_PASSWORD = "wrong-test-password";
    private static final String POSTGRES_BCRYPT_HASH =
            "$2a$10$beTr810hWWlJNdwNL8VrvezFybE6YrWMCs8Mwg1.lMDPDzSqCjEeq";

    @Autowired MockMvc mockMvc;
    @Autowired ApplicationUserRepository users;
    @Autowired ApplicationUserSiteRepository userSites;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ProductionSiteService productionSites;
    @Autowired UserDetailsService userDetailsService;
    @Autowired AuthenticationProvider authenticationProvider;
    @Autowired AuthService authService;

    @BeforeEach
    void setUp() {
        userSites.deleteAll();
        users.deleteAll();
    }

    @Test
    void databaseModeUsesPersistedUsersAndRejectsLocalCredentials() throws Exception {
        assertThat(userDetailsService).isInstanceOf(DatabaseUserDetailsService.class);
        assertThat(authenticationProvider).isInstanceOf(DatabaseAuthenticationProvider.class);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "local-admin",
                                  "password": "local-password",
                                  "productionSite": "PT"
                                }
                """))
                .andExpect(status().isUnauthorized());

        createUser("db-admin", passwordEncoder.encode(TEST_PASSWORD), true);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "db-admin",
                                  "password": "correct-test-password",
                                  "productionSite": "PT"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void postgresqlBcryptHashIsAcceptedBySpringBCryptPasswordEncoder() {
        assertThat(POSTGRES_BCRYPT_HASH).hasSize(60).startsWith("$2a$10$");
        assertThat(passwordEncoder.matches(TEST_PASSWORD, POSTGRES_BCRYPT_HASH)).isTrue();
    }

    @Test
    void diogoIsFoundBySubmittedUsernameAndCaseInsensitiveRule() {
        createUser("diogo", passwordEncoder.encode(TEST_PASSWORD), true);

        assertThat(users.findWithDriverByNormalizedUsername("diogo")).isPresent();
        assertThat(users.findWithDriverByNormalizedUsername("diogo")).map(ApplicationUserEntity::getUsername)
                .contains("diogo");
    }

    @Test
    void loginUsernameIsCaseInsensitive() throws Exception {
        createUser("diogo", passwordEncoder.encode(TEST_PASSWORD), true);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "DiOgO",
                                  "password": "correct-test-password",
                                  "productionSite": "PT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("diogo"));
    }

    @Test
    void activeUserCanAuthenticateWithFrontendJsonPayload() throws Exception {
        createUser("diogo", POSTGRES_BCRYPT_HASH, true);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "diogo",
                                  "password": "correct-test-password",
                                  "productionSite": "PT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.username").value("diogo"))
                .andExpect(jsonPath("$.user.productionSite.code").value("PT"));
    }

    @Test
    void missingUserIsRejectedWithGenericError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "missing-user",
                                  "password": "correct-test-password",
                                  "productionSite": "PT"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void wrongPasswordIsRejectedAndInternalCauseIsOnlyLogged(CapturedOutput output) throws Exception {
        createUser("diogo", passwordEncoder.encode(TEST_PASSWORD), true);

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Correlation-ID", "test-correlation-401")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "diogo",
                                  "password": "wrong-test-password",
                                  "productionSite": "PT"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-ID", "test-correlation-401"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."))
                .andExpect(jsonPath("$.details").isEmpty());

        assertThat(output).contains("test-correlation-401", "usernameNormalized=diogo",
                "encoder=BCryptPasswordEncoder", "failureReason=PASSWORD_MISMATCH", "outcome=FAILURE");
        assertThat(output).doesNotContain(TEST_PASSWORD, WRONG_TEST_PASSWORD, POSTGRES_BCRYPT_HASH);
    }

    @Test
    void diogoLoginThroughEndpointServiceMethodShowsExactPipeline(CapturedOutput output) {
        createUser("diogo", POSTGRES_BCRYPT_HASH, true);
        assertThat(users.findWithDriverByNormalizedUsername("diogo")).isPresent();

        MDC.put("correlationId", "direct-service-diogo");
        try {
            authService.login(new LoginRequest("diogo", TEST_PASSWORD, "PT"));
        } finally {
            MDC.remove("correlationId");
        }

        assertThat(output).contains(
                "direct-service-diogo",
                "auth.login.service.enter",
                "authenticationProvider=DatabaseAuthenticationProvider",
                "authenticationManager=NOT_USED_CUSTOM_PROVIDER",
                "auth.login.repository.lookup",
                "userFound=true",
                "auth.login.account.state",
                "active=true",
                "locked=NOT_MAPPED",
                "status=NOT_MAPPED",
                "lockedUntil=NOT_MAPPED",
                "encoder=BCryptPasswordEncoder",
                "matches=true",
                "auth.login.authenticatedAccount.create",
                "auth.login.session.create",
                "status=NOT_CREATED_STATELESS_TOKEN_AUTH",
                "auth.login.token.create",
                "result=SUCCESS");
        assertThat(output).doesNotContain(TEST_PASSWORD, POSTGRES_BCRYPT_HASH);
    }

    @Test
    void diogoLoginThroughEndpointServiceMethodIdentifiesBcryptMismatch(CapturedOutput output) {
        createUser("diogo", POSTGRES_BCRYPT_HASH, true);

        MDC.put("correlationId", "direct-service-diogo-fail");
        try {
            assertThatThrownBy(() -> authService.login(new LoginRequest("diogo", WRONG_TEST_PASSWORD, "PT")))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        } finally {
            MDC.remove("correlationId");
        }

        assertThat(output).contains(
                "direct-service-diogo-fail",
                "auth.login.repository.lookup",
                "userFound=true",
                "encoder=BCryptPasswordEncoder",
                "matches=false",
                "failureReason=PASSWORD_MISMATCH");
        assertThat(output).doesNotContain(TEST_PASSWORD, WRONG_TEST_PASSWORD, POSTGRES_BCRYPT_HASH);
    }

    @Test
    void disabledUserIsRejectedWithSanitizedCategory(CapturedOutput output) throws Exception {
        createUser("diogo", POSTGRES_BCRYPT_HASH, false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Correlation-ID", "test-disabled-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "diogo",
                                  "password": "correct-test-password",
                                  "productionSite": "PT"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));

        assertThat(output).contains("test-disabled-user", "active=false", "failureReason=ACCOUNT_DISABLED");
        assertThat(output).doesNotContain(TEST_PASSWORD, POSTGRES_BCRYPT_HASH);
    }

    private ApplicationUserEntity createUser(String username, String passwordHash, boolean active) {
        ApplicationUserEntity admin = new ApplicationUserEntity();
        admin.setUsername(username);
        admin.setDisplayName("Administrador DB");
        admin.setRole(UserRole.ADMIN);
        admin.setPasswordHash(passwordHash);
        admin.setActive(active);
        admin.setCreatedBy("TEST");
        admin.setUpdatedBy("TEST");
        ApplicationUserEntity saved = users.save(admin);
        productionSites.ensureUserAssociation(saved, productionSites.requireByCode(ProductionSiteCode.PT), "TEST");
        return saved;
    }
}
