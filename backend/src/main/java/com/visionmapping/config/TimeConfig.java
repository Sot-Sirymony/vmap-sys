package com.visionmapping.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the system clock as an injectable bean so that time-dependent logic
 * (overdue detection, "due this week", progress-trend windows) reads "now"
 * through a seam the tests can replace with a fixed instant.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
