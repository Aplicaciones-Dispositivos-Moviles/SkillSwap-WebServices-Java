package com.innovify.skillswap.iam.infrastructure.seeding;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Runs the {@link CoordinatorSeeder} once the application has started. */
@Component
public class CoordinatorSeedRunner implements ApplicationRunner {

    private final CoordinatorSeeder seeder;

    public CoordinatorSeedRunner(CoordinatorSeeder seeder) {
        this.seeder = seeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seeder.seed();
    }
}
