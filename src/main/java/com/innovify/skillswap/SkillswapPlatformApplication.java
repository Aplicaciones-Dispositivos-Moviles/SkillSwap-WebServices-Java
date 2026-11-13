package com.innovify.skillswap;

import com.innovify.skillswap.shared.infrastructure.persistence.DatabaseDefaults;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SkillswapPlatformApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(SkillswapPlatformApplication.class);
        // Lowest precedence: an explicit spring.datasource.url (local development) wins over DATABASE_URL.
        application.setDefaultProperties(DatabaseDefaults.fromDatabaseUrl(System.getenv("DATABASE_URL")));
        application.run(args);
    }
}
