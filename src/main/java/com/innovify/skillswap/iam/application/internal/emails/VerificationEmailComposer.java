package com.innovify.skillswap.iam.application.internal.emails;

import com.innovify.skillswap.iam.application.internal.outboundservices.EmailMessage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Writes the verification email in neutral Spanish (the students are Peruvian university students). The link
 * opens {@code GET /api/v1/authentication/verify-email?token=...} on the configured base URL.
 */
public class VerificationEmailComposer {

    public static final String VERIFY_PATH = "/api/v1/authentication/verify-email";
    static final String SUBJECT = "Verifica tu correo institucional en SkillSwap";

    private final String baseUrl;

    /** @param baseUrl the public URL of the backend (or of a page that serves the same path) */
    public VerificationEmailComposer(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("The verification base URL is required.");
        }
        String stripped = baseUrl.strip();
        this.baseUrl = stripped.endsWith("/") ? stripped.substring(0, stripped.length() - 1) : stripped;
    }

    /** The link that verifies the account. */
    public String linkFor(String token) {
        return baseUrl + VERIFY_PATH + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    /**
     * @param issuedAt  when the token was issued
     * @param expiresAt when it expires, to tell the student how long the link lasts
     */
    public EmailMessage compose(String username, String email, String token, Instant issuedAt, Instant expiresAt) {
        String link = linkFor(token);
        String validity = describe(Duration.between(issuedAt, expiresAt));

        String text = """
                Hola, %s:

                Gracias por registrarte en SkillSwap. Para activar tu cuenta, confirma tu correo institucional \
                abriendo este enlace:

                %s

                El enlace vence en %s y solo se puede usar una vez. Si vence, inicia sesión en la app y te \
                enviaremos uno nuevo.

                Si no creaste una cuenta en SkillSwap, ignora este mensaje.

                El equipo de SkillSwap
                """.formatted(username, link, validity);

        String html = """
                <!doctype html>
                <html lang="es">
                <body style="font-family:Arial,Helvetica,sans-serif;color:#1f2937;line-height:1.5">
                  <p>Hola, %s:</p>
                  <p>Gracias por registrarte en SkillSwap. Para activar tu cuenta, confirma tu correo institucional.</p>
                  <p><a href="%s" style="display:inline-block;padding:12px 20px;background:#4f46e5;color:#ffffff;\
                text-decoration:none;border-radius:6px">Verificar mi correo</a></p>
                  <p>Si el botón no funciona, copia y pega este enlace en tu navegador:<br><a href="%s">%s</a></p>
                  <p>El enlace vence en %s y solo se puede usar una vez. Si vence, inicia sesión en la app y te \
                enviaremos uno nuevo.</p>
                  <p>Si no creaste una cuenta en SkillSwap, ignora este mensaje.</p>
                  <p>El equipo de SkillSwap</p>
                </body>
                </html>
                """.formatted(escape(username), escape(link), escape(link), escape(link), validity);

        return new EmailMessage(email, username, SUBJECT, text, html);
    }

    /** "24 horas", "1 hora", "30 minutos". */
    static String describe(Duration validity) {
        long hours = validity.toHours();
        if (hours >= 1 && validity.toMinutesPart() == 0) {
            return hours == 1 ? "1 hora" : hours + " horas";
        }
        long minutes = Math.max(1, validity.toMinutes());
        return minutes == 1 ? "1 minuto" : minutes + " minutos";
    }

    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
