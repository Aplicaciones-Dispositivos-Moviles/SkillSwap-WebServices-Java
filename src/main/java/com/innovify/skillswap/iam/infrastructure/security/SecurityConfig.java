package com.innovify.skillswap.iam.infrastructure.security;

import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.application.queryservices.UserQueryService;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Stateless JWT security. Anonymous: health check, sign-up / sign-in, the API docs and the RevenueCat webhook
 * (which checks its own shared secret). Everything else needs a valid token (401 otherwise); role checks use
 * {@code @PreAuthorize} (403 with a JSON message).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    static final String FORBIDDEN_BODY = "{\"message\":\"Insufficient role for this operation.\"}";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, TokenGenerator tokenGenerator,
                                                   UserQueryService userQueryService,
                                                   CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write(FORBIDDEN_BODY);
                        }))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/", "/swagger", "/health", "/api/v1/authentication/**",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/subscriptions/webhooks/revenuecat").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(tokenGenerator, userQueryService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
