package com.nkharade.jdanalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for requirement extraction and matching (app.matching.* in application.yml).
 */
@ConfigurationProperties(prefix = "app.matching")
public record MatchingProperties(
        double coverageThreshold,
        int maxRequirements,
        int minChunkLength
) {
    public MatchingProperties {
        if (coverageThreshold <= 0 || coverageThreshold >= 1) {
            throw new IllegalArgumentException("app.matching.coverage-threshold must be between 0 and 1");
        }
        if (maxRequirements <= 0) maxRequirements = 40;
        if (minChunkLength <= 0) minChunkLength = 20;
    }
}
