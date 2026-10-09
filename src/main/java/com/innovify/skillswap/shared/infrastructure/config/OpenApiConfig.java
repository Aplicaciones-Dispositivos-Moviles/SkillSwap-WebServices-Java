package com.innovify.skillswap.shared.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui/index.html. The "Authorize" button takes the JWT returned by sign-in. */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI skillSwapOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SkillSwap Platform API")
                        .version("v1")
                        .description("Learning paths, certificate verification, peer review, reputation and "
                                + "SkillCredits. Sign in at /api/v1/authentication/sign-in and use the token "
                                + "with the Authorize button."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
