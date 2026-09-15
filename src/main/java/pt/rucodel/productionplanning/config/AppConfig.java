package pt.rucodel.productionplanning.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import pt.rucodel.productionplanning.service.AppProperties;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {
}
