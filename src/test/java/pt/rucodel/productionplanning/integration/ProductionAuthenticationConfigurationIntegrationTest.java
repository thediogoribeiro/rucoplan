package pt.rucodel.productionplanning.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
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
import pt.rucodel.productionplanning.security.local.LocalAuthenticationProvider;
import pt.rucodel.productionplanning.security.production.DatabaseAuthenticationProvider;
import pt.rucodel.productionplanning.security.production.DatabaseUserDetailsService;
import pt.rucodel.productionplanning.service.ProductionSiteService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "production"})
@TestPropertySource(properties = {
        "app.auth.mode=DATABASE",
        "app.bootstrap-admin.enabled=false"
})
class ProductionAuthenticationConfigurationIntegrationTest {
    @Autowired ApplicationContext context;
    @Autowired AuthenticationProvider authenticationProvider;
    @Autowired UserDetailsService userDetailsService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ApplicationUserRepository users;
    @Autowired ApplicationUserSiteRepository userSites;
    @Autowired ProductionSiteService productionSites;
    @Autowired MockMvc mockMvc;

    @Test
    void productionUsesDatabaseProviderAndNeverLocalProvider() {
        assertThat(authenticationProvider).isInstanceOf(DatabaseAuthenticationProvider.class);
        assertThat(authenticationProvider.usesAuthenticationManager()).isTrue();
        assertThat(authenticationProvider.userSource()).isEqualTo("DATABASE");
        assertThat(userDetailsService).isInstanceOf(DatabaseUserDetailsService.class);
        assertThatThrownBy(() -> context.getBean(LocalAuthenticationProvider.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    void productionRejectsLocalCredentialsAndAuthenticatesAppUser() throws Exception {
        userSites.deleteAll();
        users.deleteAll();

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

        ApplicationUserEntity user = new ApplicationUserEntity();
        user.setUsername("diogo");
        user.setDisplayName("Diogo");
        user.setRole(UserRole.ADMIN);
        user.setPasswordHash(passwordEncoder.encode("database-test-password"));
        user.setActive(true);
        user.setCreatedBy("TEST");
        user.setUpdatedBy("TEST");
        ApplicationUserEntity saved = users.save(user);
        productionSites.ensureUserAssociation(saved, productionSites.requireByCode(ProductionSiteCode.PT), "TEST");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "DIOGO",
                                  "password": "database-test-password",
                                  "productionSite": "PT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("diogo"));
    }
}
