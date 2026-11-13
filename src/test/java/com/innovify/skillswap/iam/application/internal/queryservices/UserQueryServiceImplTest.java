package com.innovify.skillswap.iam.application.internal.queryservices;

import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.fakes.FakeUserRepository;
import com.innovify.skillswap.iam.domain.model.queries.GetUserByIdQuery;
import com.innovify.skillswap.iam.domain.model.queries.GetUserByUsernameQuery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserQueryServiceImplTest {

    private final FakeUserRepository repository = new FakeUserRepository();
    private final UserQueryServiceImpl service = new UserQueryServiceImpl(repository);

    @Test
    void getUserById_returnsTheUserWhenItExists() {
        repository.save(TestData.newUser());

        assertThat(service.handle(new GetUserByIdQuery(1))).isPresent();
        assertThat(service.handle(new GetUserByIdQuery(99))).isEmpty();
    }

    @Test
    void getUserByUsername_isCaseInsensitive() {
        repository.save(TestData.newUser());

        assertThat(service.handle(new GetUserByUsernameQuery("ANA"))).isPresent();
    }

    @Test
    void getUserByUsername_withAMalformedUsername_returnsEmpty() {
        repository.save(TestData.newUser());

        assertThat(service.handle(new GetUserByUsernameQuery("a b"))).isEmpty();
        assertThat(service.handle(new GetUserByUsernameQuery(null))).isEmpty();
    }
}
