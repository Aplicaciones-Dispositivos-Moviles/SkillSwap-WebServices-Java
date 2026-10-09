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
                        .description("""
                                SkillSwap verifies what a student really knows: learning paths, certificate \
                                verification, peer review, reputation and SkillCredits.

                                **How to try it:** sign up and sign in at `/api/v1/authentication`, press \
                                **Authorize** and paste only the token. Every resource belongs to its owner, \
                                taken from the token."""))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
