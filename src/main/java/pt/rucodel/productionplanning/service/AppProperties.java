package pt.rucodel.productionplanning.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String environment,
        String publicBaseUrl,
        String displayName,
        String timezone
) {
    @ConstructorBinding
    public AppProperties {
    }

    public AppProperties(String displayName, String timezone) {
        this("local", "", displayName, timezone);
    }
}
