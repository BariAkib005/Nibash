package com.nibash.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs the demo seeder at startup when launched with {@code --seed} (or {@code NIBASH_SEED=true}).
 * Safe to leave on — the seeder is idempotent.
 *
 * <p>Example: {@code ./mvnw spring-boot:run -Dspring-boot.run.arguments=--seed}
 */
@Component
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final DemoSeeder seeder;

    public SeedRunner(DemoSeeder seeder) {
        this.seeder = seeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean requested = args.containsOption("seed")
                || "true".equalsIgnoreCase(System.getenv("NIBASH_SEED"));
        if (!requested) {
            return;
        }
        log.info("--seed requested, running the demo seeder…");
        log.info(seeder.seed());
    }
}
