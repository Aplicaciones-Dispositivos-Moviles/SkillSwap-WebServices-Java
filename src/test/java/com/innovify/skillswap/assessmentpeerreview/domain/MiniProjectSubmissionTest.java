package com.innovify.skillswap.assessmentpeerreview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MiniProjectSubmissionTest {

    private static MiniProjectSubmission create(String url) {
        return new MiniProjectSubmission(3, 7, 10, "http-basics", url);
    }

    @Test
    void create_keepsTheDataAndStampsTheMoment() {
        Instant before = Instant.now();

        MiniProjectSubmission submission = create("https://github.com/ana/http-basics");

        assertThat(submission.getId()).isNull();
        assertThat(submission.getMiniProjectId()).isEqualTo(3);
        assertThat(submission.getStudentId()).isEqualTo(7);
        assertThat(submission.getPathNodeId()).isEqualTo(10);
        assertThat(submission.getSkillTag()).isEqualTo("http-basics");
        assertThat(submission.getRepositoryUrl()).isEqualTo("https://github.com/ana/http-basics");
        assertThat(submission.getSubmittedAt()).isBetween(before, Instant.now());
    }

    @Test
    void create_stripsTheLink() {
        assertThat(create("  http://gitlab.com/ana/repo  ").getRepositoryUrl()).isEqualTo("http://gitlab.com/ana/repo");
    }

    @Test
    void create_acceptsALinkOfTheMaximumLength() {
        String url = "https://github.com/" + "a".repeat(MiniProjectSubmission.MAX_REPOSITORY_URL_LENGTH - 19);

        assertThat(create(url).getRepositoryUrl()).hasSize(MiniProjectSubmission.MAX_REPOSITORY_URL_LENGTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "github.com/ana/repo", "ftp://github.com/ana/repo", "https://", "not a url",
            "javascript:alert(1)"})
    void create_withALinkThatIsNotAnHttpUrl_isRejected(String url) {
        assertThatThrownBy(() -> create(url)).isInstanceOf(DomainException.class);
    }

    @Test
    void create_withoutALink_isRejected() {
        assertThatThrownBy(() -> create(null)).isInstanceOf(DomainException.class);
    }

    @Test
    void create_withALinkThatIsTooLong_isRejected() {
        String url = "https://github.com/" + "a".repeat(MiniProjectSubmission.MAX_REPOSITORY_URL_LENGTH);

        assertThatThrownBy(() -> create(url)).isInstanceOf(DomainException.class).hasMessageContaining("500");
    }

    @Test
    void create_withInvalidIdsOrSkill_isRejected() {
        String url = "https://github.com/ana/repo";

        assertThatThrownBy(() -> new MiniProjectSubmission(0, 7, 10, "http-basics", url))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new MiniProjectSubmission(3, 0, 10, "http-basics", url))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new MiniProjectSubmission(3, 7, 0, "http-basics", url))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new MiniProjectSubmission(3, 7, 10, " ", url)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new MiniProjectSubmission(3, 7, 10, null, url)).isInstanceOf(DomainException.class);
    }
}
