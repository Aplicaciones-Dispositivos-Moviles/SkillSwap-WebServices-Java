package com.innovify.skillswap.shared.interfaces.rest;

import com.innovify.skillswap.shared.interfaces.rest.resources.HealthResource;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Needs no token and does not touch the database: safe for health checks and keep-alive pings. */
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public HealthResource checkHealth() {
        return new HealthResource("Healthy", Instant.now());
    }
}
