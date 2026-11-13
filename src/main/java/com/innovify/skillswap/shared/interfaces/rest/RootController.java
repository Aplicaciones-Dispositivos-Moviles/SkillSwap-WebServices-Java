package com.innovify.skillswap.shared.interfaces.rest;

import io.swagger.v3.oas.annotations.Hidden;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The root of the site and /swagger lead to the API documentation. Needs no token. */
@RestController
@Hidden
public class RootController {

    static final String SWAGGER_UI = "/swagger-ui/index.html";

    @GetMapping({"/", "/swagger"})
    public ResponseEntity<Void> toDocumentation() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(SWAGGER_UI)).build();
    }
}
