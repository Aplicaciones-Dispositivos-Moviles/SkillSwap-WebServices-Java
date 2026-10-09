package com.innovify.skillswap.credentialverification.domain.model.aggregates;

import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskAssessment;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskLevel;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationMethod;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationStatus;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Certificate aggregate root. Centralizes the document uploaded by a student, the data extracted from it by
 * OCR on the mobile device, and the verification state resulting from the risk evaluation. It knows nothing
 * about issuer-specific logic (SUNEDU, Coursera, etc.).
 *
 * <p>Enums and the risk assessment are mapped to their columns by the auto-applied attribute converters of the
 * infrastructure layer.
 */
@Entity
@Table(name = "certificates")
public class Certificate {

    public static final int MAX_TEXT_LENGTH = 255;
    public static final int MAX_URL_LENGTH = 2048;
    public static final int MAX_QR_PAYLOAD_LENGTH = 4096;
    public static final int MAX_OCR_TEXT_LENGTH = 50000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "owner_id", nullable = false)
    private int ownerId;

    @Column(name = "holder_name", length = MAX_TEXT_LENGTH)
    private String holderName;

    @Column(name = "institution_name", length = MAX_TEXT_LENGTH)
    private String institutionName;

    @Column(name = "course_name", length = MAX_TEXT_LENGTH)
    private String courseName;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "duration_hours")
    private Integer durationHours;

    @Column(name = "certificate_number", length = MAX_TEXT_LENGTH)
    private String certificateNumber;

    @Column(name = "verification_code", length = MAX_TEXT_LENGTH)
    private String verificationCode;

    @Column(name = "verification_url", length = MAX_URL_LENGTH)
    private String verificationUrl;

    @Column(name = "qr_payload", length = MAX_QR_PAYLOAD_LENGTH)
    private String qrPayload;

    @Column(name = "ocr_text", nullable = false, columnDefinition = "text")
    private String ocrText = "";

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "storage_reference", nullable = false, length = 512)
    private String storageReference;

    @Column(name = "status", nullable = false, length = 20)
    private VerificationStatus status;

    @Column(name = "verification_method", nullable = false, length = 20)
    private VerificationMethod verificationMethod;

    // Only the score is stored: the risk level is always derived from it by the value object.
    @Column(name = "risk_score")
    private RiskAssessment riskAssessment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** Required by JPA. */
    protected Certificate() {
    }

    /**
     * Registers an uploaded document in the {@link VerificationStatus#PENDING} state, before the extracted
     * data and the risk assessment are applied.
     */
    public Certificate(int ownerId, String fileHash, String storageReference) {
        if (ownerId <= 0) {
            throw new DomainException("The certificate must belong to a valid user.");
        }
        if (fileHash == null || fileHash.isBlank()) {
            throw new DomainException("The file hash cannot be empty.");
        }
        if (storageReference == null || storageReference.isBlank()) {
            throw new DomainException("The storage reference cannot be empty.");
        }

        this.ownerId = ownerId;
        this.fileHash = fileHash.strip().toLowerCase(Locale.ROOT);
        this.storageReference = storageReference.strip();
        this.status = VerificationStatus.PENDING;
        this.verificationMethod = VerificationMethod.OCR_ONLY;
        this.createdAt = Instant.now();
    }

    /**
     * Completes the aggregate with the data read from the document. Every field is optional: the OCR may not
     * have been able to read some of them. Blank values are stored as null; the certificate number and
     * verification code are stored in uppercase so duplicates are detected regardless of case.
     *
     * @throws DomainException when the certificate is not pending or a value exceeds its maximum length
     */
    public Certificate applyExtractedData(String holderName, String institutionName, String courseName,
                                          LocalDate issueDate, Integer durationHours, String certificateNumber,
                                          String verificationCode, String verificationUrl, String qrPayload,
                                          String ocrText) {
        ensureStatus(VerificationStatus.PENDING, "Extracted data can only be applied to a pending certificate.");

        this.holderName = clean(holderName, MAX_TEXT_LENGTH, "holderName");
        this.institutionName = clean(institutionName, MAX_TEXT_LENGTH, "institutionName");
        this.courseName = clean(courseName, MAX_TEXT_LENGTH, "courseName");
        this.issueDate = issueDate;
        this.durationHours = durationHours;
        this.certificateNumber = upper(clean(certificateNumber, MAX_TEXT_LENGTH, "certificateNumber"));
        this.verificationCode = upper(clean(verificationCode, MAX_TEXT_LENGTH, "verificationCode"));
        this.verificationUrl = clean(verificationUrl, MAX_URL_LENGTH, "verificationUrl");
        this.qrPayload = clean(qrPayload, MAX_QR_PAYLOAD_LENGTH, "qrPayload");
        String cleanedOcrText = clean(ocrText, MAX_OCR_TEXT_LENGTH, "ocrText");
        this.ocrText = cleanedOcrText == null ? "" : cleanedOcrText;
        return this;
    }

    /**
     * Whether the extracted data shows OCR inconsistencies: the holder, institution, course or issue date
     * could not be read, the issue date is in the future, or the duration is not positive.
     *
     * @param today the current date, received as a parameter to keep the domain deterministic
     */
    public boolean hasOcrInconsistencies(LocalDate today) {
        return holderName == null
                || institutionName == null
                || courseName == null
                || issueDate == null
                || issueDate.isAfter(today)
                || (durationHours != null && durationHours <= 0);
    }

    /**
     * Assigns the risk evaluation and moves the certificate to {@link VerificationStatus#SUSPICIOUS} when the
     * level is high risk, or to {@link VerificationStatus#UNVERIFIED} otherwise (low risk or review).
     *
     * @throws DomainException when the certificate is not pending
     */
    public Certificate assessRisk(RiskAssessment riskAssessment) {
        ensureStatus(VerificationStatus.PENDING, "Risk can only be assessed on a pending certificate.");

        this.riskAssessment = riskAssessment;
        this.status = riskAssessment.level() == RiskLevel.HIGH_RISK
                ? VerificationStatus.SUSPICIOUS
                : VerificationStatus.UNVERIFIED;
        return this;
    }

    /**
     * Applies the decision of the verifier who reviews an escalated certificate, moving it to
     * {@link VerificationStatus#VERIFIED} or {@link VerificationStatus#REJECTED}.
     *
     * @throws DomainException when the certificate is not suspicious
     */
    public Certificate resolveDispute(boolean isAuthentic) {
        ensureStatus(VerificationStatus.SUSPICIOUS, "Only a suspicious certificate can be resolved.");

        this.status = isAuthentic ? VerificationStatus.VERIFIED : VerificationStatus.REJECTED;
        this.verificationMethod = VerificationMethod.MANUAL;
        this.verifiedAt = Instant.now();
        return this;
    }

    private void ensureStatus(VerificationStatus expected, String message) {
        if (status != expected) {
            throw new DomainException(message);
        }
    }

    private static String clean(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() > maxLength) {
            throw new DomainException("%s cannot exceed %d characters.".formatted(field, maxLength));
        }
        return trimmed;
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    /** Null until the certificate is persisted. */
    public Integer getId() {
        return id;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public String getHolderName() {
        return holderName;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public String getCourseName() {
        return courseName;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public Integer getDurationHours() {
        return durationHours;
    }

    public String getCertificateNumber() {
        return certificateNumber;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public String getVerificationUrl() {
        return verificationUrl;
    }

    public String getQrPayload() {
        return qrPayload;
    }

    public String getOcrText() {
        return ocrText;
    }

    public String getFileHash() {
        return fileHash;
    }

    public String getStorageReference() {
        return storageReference;
    }

    public VerificationStatus getStatus() {
        return status;
    }

    public VerificationMethod getVerificationMethod() {
        return verificationMethod;
    }

    /** Null until the risk is assessed. */
    public RiskAssessment getRiskAssessment() {
        return riskAssessment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Null until a verifier resolves the certificate. */
    public Instant getVerifiedAt() {
        return verifiedAt;
    }
}
