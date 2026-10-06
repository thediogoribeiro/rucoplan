package pt.rucodel.productionplanning.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class TelegramConfigurationGuard implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramConfigurationGuard.class);

    private final TelegramProperties properties;
    private final String appEnvironment;

    public TelegramConfigurationGuard(TelegramProperties properties, Environment environment) {
        this.properties = properties;
        this.appEnvironment = environment.getProperty("app.environment", "local");
    }

    @Override
    public void run(ApplicationArguments args) {
        properties.validateActiveEnvironment(appEnvironment);
        if (properties.enabled()) {
            LOGGER.info("telegram.configuration.loaded environment={} bot={}",
                    appEnvironment, properties.botKind(appEnvironment));
        } else {
            LOGGER.info("telegram.configuration.loaded environment={} bot=disabled", appEnvironment);
        }
    }
}
