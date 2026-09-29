package pt.rucodel.productionplanning.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class HerokuDatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank() || environment.containsProperty("JDBC_DATABASE_URL")) {
            return;
        }
        URI uri = URI.create(databaseUrl);
        String userInfo = uri.getUserInfo() == null ? "" : uri.getUserInfo();
        String[] credentials = userInfo.split(":", 2);
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost()
                + (uri.getPort() == -1 ? "" : ":" + uri.getPort())
                + uri.getPath()
                + "?sslmode=require";
        Map<String, Object> properties = new HashMap<>();
        properties.put("JDBC_DATABASE_URL", jdbcUrl);
        if (credentials.length > 0) {
            properties.put("JDBC_DATABASE_USERNAME", credentials[0]);
        }
        if (credentials.length > 1) {
            properties.put("JDBC_DATABASE_PASSWORD", credentials[1]);
        }
        environment.getPropertySources().addFirst(new MapPropertySource("herokuDatabaseUrl", properties));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
