package pt.rucodel.productionplanning.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import pt.rucodel.productionplanning.service.AppProperties;
import pt.rucodel.productionplanning.whatsapp.WhatsAppProperties;

@Configuration
@EnableConfigurationProperties({AppProperties.class, WhatsAppProperties.class})
public class AppConfig {
}
