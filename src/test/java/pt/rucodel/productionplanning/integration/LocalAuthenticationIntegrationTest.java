package pt.rucodel.productionplanning.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pt.rucodel.productionplanning.repository.ApplicationUserRepository;
import pt.rucodel.productionplanning.domain.UserRole;
import pt.rucodel.productionplanning.entity.ApplicationUserEntity;
import pt.rucodel.productionplanning.security.AuthenticatedUser;
import pt.rucodel.productionplanning.service.StatusHistoryActorService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
@TestPropertySource(properties = {
        "app.auth.mode=LOCAL",
        "app.bootstrap-admin.enabled=true",
        "app.bootstrap-admin.username=local-admin",
        "app.bootstrap-admin.password=local-password",
        "app.bootstrap-admin.display-name=Administrador Local"
})
class LocalAuthenticationIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ApplicationUserRepository users;
    @Autowired UserDetailsService userDetailsService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StatusHistoryActorService statusHistoryActors;

    @BeforeEach
    void cleanUsers() {
        users.deleteAll();
    }

    @Test
    void localLoginUsesConfiguredCredentialsWithoutPersistedUsers() throws Exception {
        assertThat(userDetailsService).isInstanceOf(InMemoryUserDetailsManager.class);
        assertThat(users.count()).isZero();

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "local-admin",
                                  "password": "local-password",
                                  "productionSite": "PT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("local-admin"))
                .andExpect(jsonPath("$.user.displayName").value("Administrador Local"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();

        String token = response.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("local-admin"));

        assertThat(users.count()).isZero();
    }

    @Test
    void localLoginIgnoresExistingAppUserRows() throws Exception {
        ApplicationUserEntity persisted = new ApplicationUserEntity();
        persisted.setUsername("local-admin");
        persisted.setDisplayName("Persisted User");
        persisted.setRole(UserRole.ADMIN);
        persisted.setPasswordHash(passwordEncoder.encode("different-password"));
        persisted.setActive(true);
        persisted.setCreatedBy("TEST");
        persisted.setUpdatedBy("TEST");
        users.save(persisted);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "local-admin",
                                  "password": "local-password",
                                  "productionSite": "LUX"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.displayName").value("Administrador Local"))
                .andExpect(jsonPath("$.user.productionSite.code").value("LUX"));
    }

    @Test
    void localAuthenticatedUserIsMappedToPersistedInactiveAuditActor() {
        AuthenticatedUser localUser = new AuthenticatedUser(
                UUID.nameUUIDFromBytes("local-admin:local-admin".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "local-admin",
                "Administrador Local",
                UserRole.ADMIN,
                null);

        UUID actorId = statusHistoryActors.authenticatedActorId(localUser);

        ApplicationUserEntity actor = users.findById(actorId).orElseThrow();
        assertThat(actor.getUsername()).isEqualTo("local-admin");
        assertThat(actor.getDisplayName()).isEqualTo("Administrador Local");
        assertThat(actor.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(actor.isActive()).isFalse();
        assertThat(actor.getPasswordHash()).isEqualTo("!DISABLED_AUDIT_ACTOR!");
    }
}
