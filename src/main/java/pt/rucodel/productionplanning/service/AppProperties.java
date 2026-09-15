package pt.rucodel.productionplanning.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String displayName,
        String timezone
) {
}
