package pt.rucodel.productionplanning.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import pt.rucodel.productionplanning.security.production.DatabaseUserDetailsService;
import pt.rucodel.productionplanning.service.ProductionSiteService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.auth.mode=DATABASE")
class DatabaseAuthenticationIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ApplicationUserRepository users;
    @Autowired ApplicationUserSiteRepository userSites;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ProductionSiteService productionSites;
    @Autowired UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userSites.deleteAll();
        users.deleteAll();
    }

    @Test
    void databaseModeUsesPersistedUsersAndRejectsLocalCredentials() throws Exception {
        assertThat(userDetailsService).isInstanceOf(DatabaseUserDetailsService.class);

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

        ApplicationUserEntity admin = new ApplicationUserEntity();
        admin.setUsername("db-admin");
        admin.setDisplayName("Administrador DB");
        admin.setRole(UserRole.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode("db-password"));
        admin.setActive(true);
        admin.setCreatedBy("TEST");
        admin.setUpdatedBy("TEST");
        ApplicationUserEntity saved = users.save(admin);
        productionSites.ensureUserAssociation(saved, productionSites.requireByCode(ProductionSiteCode.PT), "TEST");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "db-admin",
                                  "password": "db-password",
                                  "productionSite": "PT"
                                }
                                """))
                .andExpect(status().isOk());
    }
}
