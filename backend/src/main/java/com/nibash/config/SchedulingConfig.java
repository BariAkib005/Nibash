package com.nibash.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the daily invoice jobs. {@code nibash.jobs.enabled=false} switches them off — the test
 * profile does, so a suite that happens to run across 08:00 never sends real email.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "nibash.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
