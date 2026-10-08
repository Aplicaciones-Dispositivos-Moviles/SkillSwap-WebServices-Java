package com.innovify.skillswap.credentialverification.application.internal.commandservices;

import com.innovify.skillswap.credentialverification.application.fakes.FakeCertificateRepository;
import com.innovify.skillswap.credentialverification.application.fakes.FakeFileStorageService;
import com.innovify.skillswap.credentialverification.domain.model.CredentialVerificationError;
import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.domain.model.commands.ResolveCertificateDisputeCommand;
import com.innovify.skillswap.credentialverification.domain.model.commands.UploadCertificateCommand;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskLevel;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationMethod;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationStatus;
import com.innovify.skillswap.credentialverification.domain.services.DefaultCertificateRiskScorer;
import com.innovify.skillswap.shared.application.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateCommandServiceImplTest {

    private static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46);
    private static final byte[] OTHER_JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE1, 0x01, 0x02, 0x03);
    private static final byte[] PNG = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00);
    private static final byte[] PDF = bytes(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34);

    private final FakeCertificateRepository repository = new FakeCertificateRepository();
    private final FakeFileStorageService storage = new FakeFileStorageService();
    private CertificateCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        LocaleContextHolder.setLocale(Locale.US);
        service = new CertificateCommandServiceImpl(repository, new DefaultCertificateRiskScorer(), storage,
                messages);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static UploadCertificateCommand upload(int ownerId, byte[] content, String contentType,
                                                   String holder, String number, String code) {
        return new UploadCertificateCommand(ownerId, contentType, content, holder, "Coursera", "Backend",
                LocalDate.of(2025, 3, 10), 40, number, code, null, null, "ocr text");
    }

    private static UploadCertificateCommand upload(int ownerId, byte[] content, String contentType) {
        return upload(ownerId, content, contentType, "Ana Perez", "cert-001", "code-xyz");
    }

    private static UploadCertificateCommand upload(int ownerId, byte[] content) {
        return upload(ownerId, content, "image/jpeg");
    }

    private static void assertFailure(Result<?> result, CredentialVerificationError expected) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(expected);
    }

    /** Another user's certificate registered with the same number, code and the given file. */
    private Certificate seedOtherUser(int ownerId, byte[] file, String number, String code) {
        Certificate other = new Certificate(ownerId, sha256(file), "stored/other")
                .applyExtractedData("Luis", "Coursera", "Backend", LocalDate.of(2025, 1, 1), 10, number, code,
                        null, null, "");
        return repository.save(other);
    }

    // ---------- Upload ----------

    @Test
    void upload_withValidData_createsUnverifiedCertificateWithNoRisk() {
        Result<Certificate> result = service.handle(upload(7, JPEG));

        assertThat(result.isSuccess()).isTrue();
        Certificate certificate = result.value();
        assertThat(certificate.getId()).isNotNull();
        assertThat(certificate.getOwnerId()).isEqualTo(7);
        assertThat(certificate.getStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
        assertThat(certificate.getVerificationMethod()).isEqualTo(VerificationMethod.OCR_ONLY);
        assertThat(certificate.getRiskAssessment().score()).isZero();
        assertThat(certificate.getCertificateNumber()).isEqualTo("CERT-001");
        assertThat(certificate.getFileHash()).isEqualTo(sha256(JPEG));
        assertThat(repository.certificates()).hasSize(1);
    }

    static Stream<Arguments> acceptedFiles() {
        return Stream.of(
                Arguments.of(JPEG, "image/jpeg"),
                Arguments.of(JPEG, "image/jpg"),
                Arguments.of(PNG, "image/png"),
                Arguments.of(PDF, "application/pdf"),
                Arguments.of(PDF, "Application/PDF"));
    }

    @ParameterizedTest
    @MethodSource("acceptedFiles")
    void upload_withSupportedFile_isAccepted(byte[] content, String contentType) {
        Result<Certificate> result = service.handle(upload(7, content, contentType));

        assertThat(result.isSuccess()).isTrue();
        assertThat(storage.uploads()).hasSize(1);
    }

    @Test
    void upload_storesTheFileUnderAKeyBuiltFromOwnerAndHash() {
        Result<Certificate> result = service.handle(upload(7, JPEG));

        String key = "certificates/7/" + sha256(JPEG);
        assertThat(storage.uploads()).singleElement().satisfies(upload -> {
            assertThat(upload.key()).isEqualTo(key);
            assertThat(upload.contentType()).isEqualTo("image/jpeg");
            assertThat(upload.length()).isEqualTo(JPEG.length);
        });
        assertThat(result.value().getStorageReference()).isEqualTo("stored/" + key);
    }

    @Test
    void upload_withoutOcrData_scoresOcrInconsistencies() {
        Result<Certificate> result = service.handle(upload(7, JPEG, "image/jpeg", null, "cert-001", "code-xyz"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getRiskAssessment().score()).isEqualTo(15);
        assertThat(result.value().getStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
    }

    @Test
    void upload_withNumberUsedByAnotherUser_isReview() {
        seedOtherUser(1, OTHER_JPEG, "CERT-001", "OTHER-CODE");

        Result<Certificate> result = service.handle(upload(7, JPEG));

        assertThat(result.value().getRiskAssessment().score()).isEqualTo(30);
        assertThat(result.value().getRiskAssessment().level()).isEqualTo(RiskLevel.REVIEW);
        assertThat(result.value().getStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
    }

    @Test
    void upload_withNumberAndCodeUsedByAnotherUser_isSuspicious() {
        seedOtherUser(1, OTHER_JPEG, "CERT-001", "CODE-XYZ");

        Result<Certificate> result = service.handle(upload(7, JPEG));

        assertThat(result.value().getRiskAssessment().score()).isEqualTo(60);
        assertThat(result.value().getRiskAssessment().level()).isEqualTo(RiskLevel.HIGH_RISK);
        assertThat(result.value().getStatus()).isEqualTo(VerificationStatus.SUSPICIOUS);
    }

    @Test
    void upload_withFileUploadedByAnotherUser_isSuspicious() {
        seedOtherUser(1, JPEG, "OTHER-NUMBER", "OTHER-CODE");

        Result<Certificate> result = service.handle(upload(7, JPEG));

        assertThat(result.value().getRiskAssessment().score()).isEqualTo(50);
        assertThat(result.value().getStatus()).isEqualTo(VerificationStatus.SUSPICIOUS);
    }

    @Test
    void upload_withNumberUsedByTheSameOwner_isNotADuplicate() {
        seedOtherUser(7, OTHER_JPEG, "CERT-001", "CODE-XYZ");

        Result<Certificate> result = service.handle(upload(7, JPEG));

        assertThat(result.value().getRiskAssessment().score()).isZero();
    }

    @Test
    void upload_comparesNumbersIgnoringCaseAndWhitespace() {
        seedOtherUser(1, OTHER_JPEG, "CERT-001", "OTHER-CODE");

        Result<Certificate> result = service.handle(
                upload(7, JPEG, "image/jpeg", "Ana Perez", "  Cert-001 ", "code-xyz"));

        assertThat(result.value().getRiskAssessment().score()).isEqualTo(30);
    }

    @Test
    void upload_withSameFileFromSameOwner_isRejectedAsDuplicateWithTheExistingId() {
        Certificate first = service.handle(upload(7, JPEG)).value();

        Result<Certificate> second = service.handle(upload(7, JPEG));

        assertFailure(second, CredentialVerificationError.DUPLICATE_FILE);
        assertThat(second.details()).containsEntry("existingCertificateId", first.getId());
        assertThat(storage.uploads()).hasSize(1);
        assertThat(repository.certificates()).hasSize(1);
    }

    @Test
    void upload_withEmptyFile_isRejected() {
        Result<Certificate> result = service.handle(upload(7, new byte[0]));

        assertFailure(result, CredentialVerificationError.FILE_REQUIRED);
        assertThat(storage.uploads()).isEmpty();
    }

    @Test
    void upload_withFileOverTheLimit_isRejected() {
        byte[] big = new byte[CertificateCommandServiceImpl.MAX_FILE_SIZE_BYTES + 1];
        System.arraycopy(JPEG, 0, big, 0, JPEG.length);

        Result<Certificate> result = service.handle(upload(7, big));

        assertFailure(result, CredentialVerificationError.FILE_TOO_LARGE);
        assertThat(storage.uploads()).isEmpty();
    }

    @Test
    void upload_withFileExactlyAtTheLimit_isAccepted() {
        byte[] exact = new byte[CertificateCommandServiceImpl.MAX_FILE_SIZE_BYTES];
        System.arraycopy(JPEG, 0, exact, 0, JPEG.length);

        Result<Certificate> result = service.handle(upload(7, exact));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void upload_withUnsupportedType_isRejected() {
        Result<Certificate> result = service.handle(upload(7, "hello".getBytes(), "text/plain"));

        assertFailure(result, CredentialVerificationError.INVALID_FILE_TYPE);
    }

    @Test
    void upload_whenDeclaredTypeDoesNotMatchTheContent_isRejected() {
        Result<Certificate> result = service.handle(upload(7, JPEG, "image/png"));

        assertFailure(result, CredentialVerificationError.INVALID_FILE_TYPE);
        assertThat(storage.uploads()).isEmpty();
    }

    @Test
    void upload_withRenamedScriptDeclaredAsImage_isRejected() {
        Result<Certificate> result = service.handle(upload(7, "#!/bin/sh\nrm -rf /".getBytes(), "image/jpeg"));

        assertFailure(result, CredentialVerificationError.INVALID_FILE_TYPE);
    }

    @Test
    void upload_withFieldOverTheLimit_isRejectedBeforeStoring() {
        Result<Certificate> result = service.handle(
                upload(7, JPEG, "image/jpeg", "x".repeat(256), "cert-001", "code-xyz"));

        assertFailure(result, CredentialVerificationError.FIELD_TOO_LONG);
        assertThat(storage.uploads()).isEmpty();
        assertThat(repository.certificates()).isEmpty();
    }

    @Test
    void upload_whenStorageFails_returnsStorageErrorAndSavesNothing() {
        storage.failOnUpload(new IllegalStateException("cloud down"));

        Result<Certificate> result = service.handle(upload(7, JPEG));

        assertFailure(result, CredentialVerificationError.STORAGE_ERROR);
        assertThat(repository.certificates()).isEmpty();
    }

    @Test
    void upload_whenSaveFails_deletesTheStoredFileAndReturnsDatabaseError() {
        repository.failOnSave(new DataIntegrityViolationException("boom"));

        Result<Certificate> result = service.handle(upload(7, JPEG));

        assertFailure(result, CredentialVerificationError.DATABASE_ERROR);
        assertThat(storage.deleted()).containsExactly("stored/certificates/7/" + sha256(JPEG));
    }

    @Test
    void upload_whenSomethingUnexpectedFails_returnsInternalServerError() {
        repository.failOnSave(new IllegalStateException("bug"));

        Result<Certificate> result = service.handle(upload(7, JPEG));

        assertFailure(result, CredentialVerificationError.INTERNAL_SERVER_ERROR);
        assertThat(storage.deleted()).hasSize(1);
    }

    @Test
    void upload_failureMessage_followsTheRequestLocale() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("es-419"));

        Result<Certificate> result = service.handle(upload(7, new byte[0]));

        assertThat(result.message()).isEqualTo("Se requiere el archivo del certificado.");
    }

    // ---------- Resolve dispute ----------

    private Certificate suspiciousCertificate() {
        seedOtherUser(1, OTHER_JPEG, "CERT-001", "CODE-XYZ");
        return service.handle(upload(7, JPEG)).value();
    }

    @ParameterizedTest
    @MethodSource("decisions")
    void resolve_ofSuspiciousCertificate_appliesTheDecision(boolean authentic, VerificationStatus expected) {
        Certificate suspicious = suspiciousCertificate();

        Result<Certificate> result = service.handle(
                new ResolveCertificateDisputeCommand(suspicious.getId(), authentic));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getStatus()).isEqualTo(expected);
        assertThat(result.value().getVerificationMethod()).isEqualTo(VerificationMethod.MANUAL);
        assertThat(result.value().getVerifiedAt()).isNotNull();
    }

    static Stream<Arguments> decisions() {
        return Stream.of(
                Arguments.of(true, VerificationStatus.VERIFIED),
                Arguments.of(false, VerificationStatus.REJECTED));
    }

    @Test
    void resolve_ofUnknownCertificate_returnsNotFound() {
        Result<Certificate> result = service.handle(new ResolveCertificateDisputeCommand(999, true));

        assertFailure(result, CredentialVerificationError.CERTIFICATE_NOT_FOUND);
    }

    @Test
    void resolve_ofNonSuspiciousCertificate_returnsInvalidTransition() {
        Certificate unverified = service.handle(upload(7, JPEG)).value();

        Result<Certificate> result = service.handle(
                new ResolveCertificateDisputeCommand(unverified.getId(), true));

        assertFailure(result, CredentialVerificationError.INVALID_STATUS_TRANSITION);
    }

    @Test
    void resolve_twice_returnsInvalidTransitionTheSecondTime() {
        Certificate suspicious = suspiciousCertificate();
        service.handle(new ResolveCertificateDisputeCommand(suspicious.getId(), true));

        Result<Certificate> second = service.handle(
                new ResolveCertificateDisputeCommand(suspicious.getId(), false));

        assertFailure(second, CredentialVerificationError.INVALID_STATUS_TRANSITION);
        assertThat(suspicious.getStatus()).isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    void resolve_whenSaveFails_returnsDatabaseError() {
        Certificate suspicious = suspiciousCertificate();
        repository.failOnSave(new DataIntegrityViolationException("boom"));

        Result<Certificate> result = service.handle(
                new ResolveCertificateDisputeCommand(suspicious.getId(), true));

        assertFailure(result, CredentialVerificationError.DATABASE_ERROR);
    }
}
