package com.nibash.seed;

import com.nibash.common.Policy;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /api/seed/} — back-office only (spec §8.25). Idempotent. */
@RestController
@RequestMapping("/api/seed")
public class SeedController {

    private final DemoSeeder seeder;

    public SeedController(DemoSeeder seeder) {
        this.seeder = seeder;
    }

    @PostMapping("/")
    public Map<String, String> seed() {
        Policy.requireBackOffice();
        return Map.of("detail", seeder.seed());
    }
}
