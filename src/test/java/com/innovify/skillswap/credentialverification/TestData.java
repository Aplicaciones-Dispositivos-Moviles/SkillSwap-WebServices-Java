package com.innovify.skillswap.credentialverification;

import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import java.time.LocalDate;

/** Shared builders for the Credential Verification tests. */
public final class TestData {

    private TestData() {
    }

    /** A pending certificate with complete data, owned by the given user. */
    public static Certificate newCertificate(int ownerId, String fileHash) {
        return new Certificate(ownerId, fileHash, "certificates/" + ownerId + "/" + fileHash + ".pdf")
                .applyExtractedData("Ana Perez", "Coursera", "Backend with Spring", LocalDate.of(2025, 3, 10), 40,
                        "cert-001", "code-xyz", "https://example.com/verify/1", null, "full text");
    }
}
