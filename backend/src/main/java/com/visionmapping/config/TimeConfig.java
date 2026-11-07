package com.visionmapping.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the system clock as an injectable bean so that time-dependent logic
 * (overdue detection, "due this week", progress-trend windows) reads "now"
 * through a seam the tests can replace with a fixed instant.
 *
 * The clock ticks in whole microseconds, the most the database keeps. On Linux
 * the system clock has nanoseconds, so without this a saved time came back in
 * the first response with nanoseconds and in every later read without them.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemDefaultZone(), Duration.ofNanos(1_000));
    }
}
