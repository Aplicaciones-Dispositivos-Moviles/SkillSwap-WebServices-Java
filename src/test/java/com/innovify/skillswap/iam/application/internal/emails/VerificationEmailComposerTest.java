package com.innovify.skillswap.iam.application.internal.emails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.iam.application.internal.outboundservices.EmailMessage;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VerificationEmailComposerTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    @Test
    void linkFor_pointsToTheVerifyEndpointOfTheBaseUrl() {
        var composer = new VerificationEmailComposer("https://api.skillswap.test/");

        assertThat(composer.linkFor("abc_DEF-123"))
                .isEqualTo("https://api.skillswap.test/api/v1/authentication/verify-email?token=abc_DEF-123");
    }

    @Test
    void compose_writesBothVersionsInNeutralSpanishWithTheLinkAndTheValidity() {
        var composer = new VerificationEmailComposer("https://api.skillswap.test");

        EmailMessage message = composer.compose("ana", "ana@upc.edu.pe", "tok3n", NOW,
                NOW.plus(Duration.ofHours(24)));

        String link = "https://api.skillswap.test/api/v1/authentication/verify-email?token=tok3n";
        assertThat(message.toAddress()).isEqualTo("ana@upc.edu.pe");
        assertThat(message.toName()).isEqualTo("ana");
        assertThat(message.subject()).isEqualTo("Verifica tu correo institucional en SkillSwap");
        assertThat(message.textContent()).contains("Hola, ana:", link, "24 horas", "solo se puede usar una vez");
        assertThat(message.htmlContent()).contains("href=\"" + link + "\"", "Verificar mi correo", "24 horas");
        // Tuteo, never voseo.
        assertThat(message.textContent() + message.htmlContent())
                .doesNotContain("confirmá", "abrí", "iniciá", "ignorá", "copiá", "vos ");
    }

    @Test
    void compose_escapesTheUsernameInTheHtmlVersion() {
        var composer = new VerificationEmailComposer("https://api.skillswap.test");

        EmailMessage message = composer.compose("<b>ana</b>", "ana@upc.edu.pe", "t", NOW, NOW.plusSeconds(3600));

        assertThat(message.htmlContent()).contains("&lt;b&gt;ana&lt;/b&gt;").doesNotContain("<b>ana</b>");
    }

    @ParameterizedTest
    @CsvSource({"PT24H,24 horas", "PT1H,1 hora", "PT30M,30 minutos", "PT1M,1 minuto", "PT90M,90 minutos"})
    void describe_writesTheValidityInSpanish(String duration, String expected) {
        assertThat(VerificationEmailComposer.describe(Duration.parse(duration))).isEqualTo(expected);
    }

    @Test
    void constructor_requiresABaseUrl() {
        assertThatThrownBy(() -> new VerificationEmailComposer(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
