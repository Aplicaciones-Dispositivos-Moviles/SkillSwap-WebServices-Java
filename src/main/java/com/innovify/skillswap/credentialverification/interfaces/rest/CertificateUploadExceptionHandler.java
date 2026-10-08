package com.innovify.skillswap.credentialverification.interfaces.rest;

import com.innovify.skillswap.credentialverification.domain.model.CredentialVerificationError;
import com.innovify.skillswap.credentialverification.interfaces.rest.transform.CredentialVerificationActionResultAssembler;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * A request over the multipart limit (25 MB, application.properties) is rejected by the framework before the
 * controller runs. It is answered with the same 413 FileTooLarge error as a file over 10 MB.
 */
@RestControllerAdvice(assignableTypes = CertificatesController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CertificateUploadExceptionHandler {

    private final MessageSource messageSource;

    public CertificateUploadExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        CredentialVerificationError error = CredentialVerificationError.FILE_TOO_LARGE;
        String code = ErrorCodes.of(error);
        String message = messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
        return CredentialVerificationActionResultAssembler.toError(error, message);
    }
}
