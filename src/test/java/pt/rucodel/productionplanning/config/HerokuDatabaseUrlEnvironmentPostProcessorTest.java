package pt.rucodel.productionplanning.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class HerokuDatabaseUrlEnvironmentPostProcessorTest {
    @Test
    void herokuDatabaseUrlIsConvertedToPostgresJdbcSettings() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgres://db_user:db_password@ec2-10-0-0-1.compute.amazonaws.com:5432/rucoplan");

        new HerokuDatabaseUrlEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("JDBC_DATABASE_URL"))
                .isEqualTo("jdbc:postgresql://ec2-10-0-0-1.compute.amazonaws.com:5432/rucoplan?sslmode=require");
        assertThat(environment.getProperty("JDBC_DATABASE_USERNAME")).isEqualTo("db_user");
        assertThat(environment.getProperty("JDBC_DATABASE_PASSWORD")).isEqualTo("db_password");
    }
}
