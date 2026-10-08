package com.innovify.skillswap.iam.infrastructure.security;

import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.application.queryservices.UserQueryService;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.queries.GetUserByIdQuery;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the Bearer JWT of each request. When the token is valid and its user still exists, the
 * {@link User} becomes the principal of the request, with the authority {@code ROLE_STUDENT} or
 * {@code ROLE_COORDINATOR}. Otherwise the request goes on unauthenticated and the security rules decide
 * (401 on protected endpoints).
 *
 * <p>It is created by {@link SecurityConfig} and is not a bean, so Spring Boot does not also register it as a
 * servlet filter.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenGenerator tokenGenerator;
    private final UserQueryService userQueryService;

    public JwtAuthenticationFilter(TokenGenerator tokenGenerator, UserQueryService userQueryService) {
        this.tokenGenerator = tokenGenerator;
        this.userQueryService = userQueryService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        extractToken(request)
                .flatMap(tokenGenerator::validateToken)
                .flatMap(userId -> userQueryService.handle(new GetUserByIdQuery(userId)))
                .ifPresent(JwtAuthenticationFilter::authenticate);

        chain.doFilter(request, response);
    }

    /** Takes the last segment of the header, so both "Bearer token" and a bare token work, like the C# API. */
    private static Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        String[] parts = header.trim().split(" ");
        return Optional.of(parts[parts.length - 1]);
    }

    private static void authenticate(User user) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
        SecurityContextHolder.setContext(context);
    }
}
