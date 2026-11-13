package com.innovify.skillswap.assessmentpeerreview.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetReevaluationCandidatesQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GetReevaluationCandidatesQueryTest {

    @Test
    void withoutLimit_usesTheDefault() {
        assertThat(new GetReevaluationCandidatesQuery(7, null, null).limit()).isEqualTo(20);
    }

    @ParameterizedTest
    @CsvSource({"0,20", "-5,20", "1,1", "50,50", "51,50", "1000,50"})
    void limit_isNormalized(int limit, int expected) {
        assertThat(new GetReevaluationCandidatesQuery(7, null, limit).limit()).isEqualTo(expected);
    }

    @Test
    void skillTag_isStrippedAndBlankMeansAll() {
        assertThat(new GetReevaluationCandidatesQuery(7, "  http-basics ", 10).skillTag()).isEqualTo("http-basics");
        assertThat(new GetReevaluationCandidatesQuery(7, "   ", 10).skillTag()).isNull();
        assertThat(new GetReevaluationCandidatesQuery(7, null, 10).skillTag()).isNull();
    }
}
