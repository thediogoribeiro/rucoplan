package pt.rucodel.productionplanning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import pt.rucodel.productionplanning.service.AppProperties;
import pt.rucodel.productionplanning.telegram.TelegramProperties;
import pt.rucodel.productionplanning.whatsapp.WhatsAppProperties;

import java.time.Clock;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({AppProperties.class, TelegramProperties.class, WhatsAppProperties.class})
public class ProductionPlanningApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductionPlanningApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
