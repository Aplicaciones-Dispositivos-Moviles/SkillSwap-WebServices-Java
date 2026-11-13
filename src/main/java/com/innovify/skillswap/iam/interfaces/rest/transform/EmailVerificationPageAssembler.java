package com.innovify.skillswap.iam.interfaces.rest.transform;

import com.innovify.skillswap.iam.domain.model.IamError;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.shared.application.Result;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The page shown when the student opens the verification link of the email in a browser: a short message in
 * neutral Spanish, the language of the email, with the HTTP status of the JSON endpoint.
 */
public final class EmailVerificationPageAssembler {

    private EmailVerificationPageAssembler() {
    }

    public static ResponseEntity<String> toPage(Result<User> result) {
        String title;
        String body;
        if (result.isSuccess()) {
            title = "¡Tu correo fue verificado!";
            body = "Ya puedes iniciar sesión en SkillSwap con tu usuario y contraseña.";
        } else if (result.error() == IamError.VERIFICATION_TOKEN_EXPIRED) {
            title = "El enlace venció";
            body = "Inicia sesión en la app y te enviaremos un nuevo correo de verificación.";
        } else if (result.error() == IamError.INVALID_VERIFICATION_TOKEN) {
            title = "El enlace no es válido";
            body = "Es posible que ya lo hayas usado o que te hayamos enviado uno más reciente. Si tu cuenta aún "
                    + "no está verificada, inicia sesión en la app y te enviaremos un nuevo correo.";
        } else {
            title = "No pudimos verificar tu correo";
            body = "Inténtalo de nuevo en unos minutos.";
        }

        String html = """
                <!doctype html>
                <html lang="es">
                <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>SkillSwap</title></head>
                <body style="font-family:Arial,Helvetica,sans-serif;color:#1f2937;max-width:32rem;margin:3rem auto;\
                padding:0 1rem;line-height:1.5">
                <h1 style="font-size:1.5rem">%s</h1>
                <p>%s</p>
                </body>
                </html>
                """.formatted(title, body);

        int status = result.isSuccess()
                ? 200
                : IamActionResultAssembler.toStatusFromIamError((IamError) result.error()).value();
        return ResponseEntity.status(status).contentType(
                new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8)).body(html);
    }
}
