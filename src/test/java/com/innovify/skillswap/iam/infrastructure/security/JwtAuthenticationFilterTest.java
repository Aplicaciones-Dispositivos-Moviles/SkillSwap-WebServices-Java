package com.innovify.skillswap.iam.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.iam.application.fakes.FakeTokenGenerator;
import com.innovify.skillswap.iam.application.fakes.FakeUserRepository;
import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.internal.queryservices.UserQueryServiceImpl;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private final FakeUserRepository users = new FakeUserRepository();
    private final FakeTokenGenerator tokens = new FakeTokenGenerator();
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(tokens, new UserQueryServiceImpl(users));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** Runs the filter and returns what the next filter in the chain saw. */
    private Authentication run(String authorizationHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).as("the request must always continue").isNotNull();
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private User savedUser(UserRole role) {
        return users.save(TestData.newUser("ana", "ana@upc.edu.pe", role));
    }

    @Test
    void validBearerToken_authenticatesTheUserWithItsRole() throws Exception {
        User user = savedUser(UserRole.STUDENT);

        Authentication authentication = run("Bearer " + tokens.generateToken(user));

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isSameAs(user);
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_STUDENT");
    }

    @Test
    void bareTokenWithoutTheBearerPrefix_alsoWorks() throws Exception {
        User user = savedUser(UserRole.STUDENT);

        assertThat(run(tokens.generateToken(user))).isNotNull();
    }

    @Test
    void missingHeader_leavesTheRequestUnauthenticated() throws Exception {
        savedUser(UserRole.STUDENT);

        assertThat(run(null)).isNull();
    }

    @Test
    void invalidToken_leavesTheRequestUnauthenticated() throws Exception {
        savedUser(UserRole.STUDENT);

        assertThat(run("Bearer garbage")).isNull();
    }

    @Test
    void tokenOfAUserThatNoLongerExists_leavesTheRequestUnauthenticated() throws Exception {
        assertThat(run("Bearer token-for-99")).isNull();
    }
}
