package com.innovify.skillswap.credentialverification.application.internal.commandservices;

import com.innovify.skillswap.credentialverification.application.commandservices.CertificateCommandService;
import com.innovify.skillswap.credentialverification.application.internal.outboundservices.FileStorageService;
import com.innovify.skillswap.credentialverification.domain.model.CredentialVerificationError;
import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.domain.model.commands.ResolveCertificateDisputeCommand;
import com.innovify.skillswap.credentialverification.domain.model.commands.UploadCertificateCommand;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationStatus;
import com.innovify.skillswap.credentialverification.domain.repositories.CertificateRepository;
import com.innovify.skillswap.credentialverification.domain.services.CertificateRiskScorer;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Certificate command service.
 *
 * <p>It is deliberately not {@code @Transactional}: each {@link CertificateRepository#save} commits on its own,
 * so a persistence failure is caught here, returned as a {@link Result}, and the file already stored is
 * deleted.
 */
@Service
public class CertificateCommandServiceImpl implements CertificateCommandService {

    private static final Logger log = LoggerFactory.getLogger(CertificateCommandServiceImpl.class);

    public static final int MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PDF_SIGNATURE = {0x25, 0x50, 0x44, 0x46, 0x2D}; // "%PDF-"

    private final CertificateRepository certificateRepository;
    private final CertificateRiskScorer riskScorer;
    private final FileStorageService fileStorageService;
    private final MessageSource messageSource;

    public CertificateCommandServiceImpl(CertificateRepository certificateRepository,
                                         CertificateRiskScorer riskScorer,
                                         FileStorageService fileStorageService,
                                         MessageSource messageSource) {
        this.certificateRepository = certificateRepository;
        this.riskScorer = riskScorer;
        this.fileStorageService = fileStorageService;
        this.messageSource = messageSource;
    }

    @Override
    public Result<Certificate> handle(UploadCertificateCommand command) {
        String[] contentType = new String[1];
        CredentialVerificationError fileError = validateFile(command, contentType);
        if (fileError != null) {
            return failure(fileError);
        }

        if (exceedsFieldLimits(command)) {
            return failure(CredentialVerificationError.FIELD_TOO_LONG);
        }

        String fileHash = sha256Hex(command.fileContent());
        var existing = certificateRepository.findByFileHash(command.ownerId(), fileHash);
        if (existing.isPresent()) {
            return Result.failure(CredentialVerificationError.DUPLICATE_FILE,
                    message(CredentialVerificationError.DUPLICATE_FILE),
                    Map.of("existingCertificateId", existing.get().getId()));
        }

        String storageReference;
        try {
            storageReference = fileStorageService.upload(command.fileContent(), contentType[0],
                    "certificates/%d/%s".formatted(command.ownerId(), fileHash));
        } catch (RuntimeException exception) {
            log.error("Could not store the certificate file of user {}", command.ownerId(), exception);
            return failure(CredentialVerificationError.STORAGE_ERROR);
        }

        try {
            Certificate certificate = new Certificate(command.ownerId(), fileHash, storageReference)
                    .applyExtractedData(command.holderName(), command.institutionName(), command.courseName(),
                            command.issueDate(), command.durationHours(), command.certificateNumber(),
                            command.verificationCode(), command.verificationUrl(), command.qrPayload(),
                            command.ocrText());

            // The aggregate normalizes the number and the code, so duplicates are looked up with its values.
            boolean duplicateNumber = certificate.getCertificateNumber() != null
                    && certificateRepository.existsByCertificateNumberExcludingOwner(
                    command.ownerId(), certificate.getCertificateNumber());
            boolean duplicateCode = certificate.getVerificationCode() != null
                    && certificateRepository.existsByVerificationCodeExcludingOwner(
                    command.ownerId(), certificate.getVerificationCode());
            boolean duplicateFile = certificateRepository.existsByFileHashExcludingOwner(
                    command.ownerId(), fileHash);

            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            certificate.assessRisk(riskScorer.calculateRisk(
                    duplicateNumber, duplicateCode, duplicateFile, certificate.hasOcrInconsistencies(today)));

            return Result.success(certificateRepository.save(certificate));
        } catch (RuntimeException exception) {
            log.error("Could not register the certificate of user {}", command.ownerId(), exception);
            deleteQuietly(storageReference);
            return failure(toError(exception));
        }
    }

    @Override
    public Result<Certificate> handle(ResolveCertificateDisputeCommand command) {
        var found = certificateRepository.findById(command.certificateId());
        if (found.isEmpty()) {
            return failure(CredentialVerificationError.CERTIFICATE_NOT_FOUND);
        }
        Certificate certificate = found.get();

        if (certificate.getStatus() != VerificationStatus.SUSPICIOUS) {
            return failure(CredentialVerificationError.INVALID_STATUS_TRANSITION);
        }

        try {
            certificate.resolveDispute(command.isAuthentic());
            return Result.success(certificateRepository.save(certificate));
        } catch (RuntimeException exception) {
            log.error("Could not resolve the certificate {}", command.certificateId(), exception);
            return failure(toError(exception));
        }
    }

    /**
     * Checks size and type. The declared content type must match the real content of the file, detected from
     * its first bytes, so a renamed file is not accepted.
     *
     * @param contentType receives the detected content type when the file is valid
     */
    private static CredentialVerificationError validateFile(UploadCertificateCommand command,
                                                            String[] contentType) {
        byte[] content = command.fileContent();
        if (content == null || content.length == 0) {
            return CredentialVerificationError.FILE_REQUIRED;
        }
        if (content.length > MAX_FILE_SIZE_BYTES) {
            return CredentialVerificationError.FILE_TOO_LARGE;
        }

        String detected = detectContentType(content);
        String declared = normalizeContentType(command.contentType());
        if (detected == null || !detected.equals(declared)) {
            return CredentialVerificationError.INVALID_FILE_TYPE;
        }

        contentType[0] = detected;
        return null;
    }

    private static String detectContentType(byte[] content) {
        if (startsWith(content, JPEG_SIGNATURE)) {
            return "image/jpeg";
        }
        if (startsWith(content, PNG_SIGNATURE)) {
            return "image/png";
        }
        if (startsWith(content, PDF_SIGNATURE)) {
            return "application/pdf";
        }
        return null;
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        return content.length >= signature.length
                && Arrays.equals(content, 0, signature.length, signature, 0, signature.length);
    }

    private static String normalizeContentType(String contentType) {
        String normalized = (contentType == null ? "" : contentType).strip().toLowerCase(Locale.ROOT);
        return normalized.equals("image/jpg") ? "image/jpeg" : normalized;
    }

    private static boolean exceedsFieldLimits(UploadCertificateCommand command) {
        return exceeds(command.holderName(), Certificate.MAX_TEXT_LENGTH)
                || exceeds(command.institutionName(), Certificate.MAX_TEXT_LENGTH)
                || exceeds(command.courseName(), Certificate.MAX_TEXT_LENGTH)
                || exceeds(command.certificateNumber(), Certificate.MAX_TEXT_LENGTH)
                || exceeds(command.verificationCode(), Certificate.MAX_TEXT_LENGTH)
                || exceeds(command.verificationUrl(), Certificate.MAX_URL_LENGTH)
                || exceeds(command.qrPayload(), Certificate.MAX_QR_PAYLOAD_LENGTH)
                || exceeds(command.ocrText(), Certificate.MAX_OCR_TEXT_LENGTH);
    }

    private static boolean exceeds(String value, int maxLength) {
        return value != null && value.strip().length() > maxLength;
    }

    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static CredentialVerificationError toError(RuntimeException exception) {
        return exception instanceof DataAccessException
                ? CredentialVerificationError.DATABASE_ERROR
                : CredentialVerificationError.INTERNAL_SERVER_ERROR;
    }

    /** Compensates an upload whose certificate could not be registered. */
    private void deleteQuietly(String storageReference) {
        try {
            fileStorageService.delete(storageReference);
        } catch (RuntimeException exception) {
            log.warn("Orphaned certificate file could not be deleted: {}", storageReference, exception);
        }
    }

    private Result<Certificate> failure(CredentialVerificationError error) {
        return Result.failure(error, message(error));
    }

    private String message(CredentialVerificationError error) {
        String code = ErrorCodes.of(error);
        return messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
    }
}
