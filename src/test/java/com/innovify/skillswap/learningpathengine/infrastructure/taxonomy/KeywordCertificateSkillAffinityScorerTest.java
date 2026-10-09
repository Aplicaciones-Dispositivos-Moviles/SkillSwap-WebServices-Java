package com.innovify.skillswap.learningpathengine.infrastructure.taxonomy;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.CertificateContent;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.SkillAffinity;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** US15: the affinity between the content of a certificate and the skills of the real catalog. */
class KeywordCertificateSkillAffinityScorerTest {

    private final KeywordCertificateSkillAffinityScorer scorer =
            new KeywordCertificateSkillAffinityScorer(SkillCatalog.loadEmbedded());

    private double affinity(String courseName, String ocrText, String skillTag) {
        return scorer.score(new CertificateContent(courseName, ocrText), List.of(skillTag)).get(0).score();
    }

    @Test
    @DisplayName("US15 escenario 1: a course about the skill covers it")
    void aCourseNameThatMentionsTheSkill_hasFullAffinity() {
        assertThat(affinity("Diseño de APIs REST con Spring", null, "rest-api-design")).isEqualTo(1.0);
        assertThat(affinity("AUTENTICACIÓN con JWT", "", "authentication-jwt")).isEqualTo(1.0);
    }

    @Test
    void aTextThatMentionsTheSkillRepeatedly_coversIt() {
        String ocr = "Certificado de finalización. Temas: servicios web, endpoints y versionado.";

        assertThat(affinity("Curso de backend avanzado 2025", ocr, "http-basics")).isEqualTo(0.0);
        assertThat(affinity("Programa de especialización", ocr, "rest-api-design")).isEqualTo(0.75);
        assertThat(new SkillAffinity("rest-api-design", 0.75).covers()).isTrue();
    }

    @Test
    @DisplayName("US15 escenario 2: an incidental mention does not cover the skill")
    void aSingleMentionInTheText_isBelowTheThreshold() {
        double score = affinity("Fundamentos de bases de datos", "Incluye una introducción a http.", "http-basics");

        assertThat(score).isEqualTo(0.5);
        assertThat(new SkillAffinity("http-basics", score).covers()).isFalse();
    }

    @Test
    void keywordsMatchOnlyAsWholeWords() {
        assertThat(affinity("Programación en JavaScript", null, "java-language")).isEqualTo(0.0);
    }

    @Test
    void anUnknownSkillOrAnEmptyCertificate_hasNoAffinity() {
        assertThat(affinity("APIs REST", null, "not-a-skill")).isEqualTo(0.0);
        assertThat(affinity(null, null, "rest-api-design")).isEqualTo(0.0);
    }

    @Test
    void scoresEverySkillInTheOrderReceived() {
        List<SkillAffinity> affinities = scorer.score(new CertificateContent("APIs REST y JWT", null),
                List.of("authentication-jwt", "sql-fundamentals", "rest-api-design"));

        assertThat(affinities).extracting(SkillAffinity::skillTag)
                .containsExactly("authentication-jwt", "sql-fundamentals", "rest-api-design");
        assertThat(affinities).extracting(SkillAffinity::score).containsExactly(1.0, 0.0, 1.0);
    }
}
