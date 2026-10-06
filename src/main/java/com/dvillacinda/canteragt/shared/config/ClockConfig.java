package com.dvillacinda.canteragt.shared.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Single time source so date-based rules (e.g. assignments in force today) can be tested deterministically. */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
