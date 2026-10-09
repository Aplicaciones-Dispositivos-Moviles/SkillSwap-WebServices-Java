package com.innovify.skillswap.moderationdisputes.application.fakes;

import com.innovify.skillswap.assessmentpeerreview.application.acl.VerifierProfileContextFacade;
import com.innovify.skillswap.assessmentpeerreview.application.acl.VerifierWorkload;
import com.innovify.skillswap.credentialverification.application.acl.CertificateReviewOutcome;
import com.innovify.skillswap.credentialverification.application.acl.CertificateReviewView;
import com.innovify.skillswap.credentialverification.application.acl.CertificateSummary;
import com.innovify.skillswap.credentialverification.application.acl.CredentialContextFacade;
import com.innovify.skillswap.reputation.application.acl.ReputationContextFacade;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** The other bounded contexts as Moderation &amp; Disputes sees them, set by each test. */
public final class FakeModerationFacades {

    private FakeModerationFacades() {
    }

    /** The available verifiers and their workload. */
    public static class Verifiers implements VerifierProfileContextFacade {

        private final List<VerifierWorkload> available = new ArrayList<>();
        private final Set<Integer> enabled = new HashSet<>();

        public Verifiers add(int userId, int openCases) {
            available.add(new VerifierWorkload(userId, openCases));
            enabled.add(userId);
            return this;
        }

        @Override
        public boolean updateRating(int verifierUserId, double rating) {
            return true;
        }

        @Override
        public boolean isEnabledVerifier(int userId) {
            return enabled.contains(userId);
        }

        @Override
        public List<VerifierWorkload> getAvailableVerifiers() {
            return List.copyOf(available);
        }
    }

    /** Who is a Verificador senior. */
    public static class Reputation implements ReputationContextFacade {

        private final Set<Integer> seniors = new HashSet<>();

        public Reputation senior(int userId) {
            seniors.add(userId);
            return this;
        }

        @Override
        public boolean isSeniorVerifier(int userId) {
            return seniors.contains(userId);
        }

        @Override
        public Set<Integer> findSeniorVerifiers(Collection<Integer> userIds) {
            return userIds.stream().filter(seniors::contains).collect(Collectors.toSet());
        }
    }

    /** Records the decisions applied to the certificates and answers the outcome set by the test. */
    public static class Credentials implements CredentialContextFacade {

        public record Decision(int certificateId, boolean authentic) {
        }

        private final List<Decision> decisions = new ArrayList<>();
        private CertificateReviewOutcome outcome = CertificateReviewOutcome.RESOLVED;

        public List<Decision> decisions() {
            return decisions;
        }

        public void answer(CertificateReviewOutcome outcome) {
            this.outcome = outcome;
        }

        @Override
        public List<CertificateSummary> getEvidenceCertificates(int ownerId) {
            return List.of();
        }

        @Override
        public Optional<CertificateReviewView> getCertificateForReview(int certificateId) {
            return Optional.empty();
        }

        @Override
        public CertificateReviewOutcome resolveSuspiciousCertificate(int certificateId, boolean authentic) {
            decisions.add(new Decision(certificateId, authentic));
            return outcome;
        }
    }
}
