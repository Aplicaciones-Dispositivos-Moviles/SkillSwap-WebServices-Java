package com.innovify.skillswap.assessmentpeerreview.application.acl;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Facade implementation over the verifier profile repository. */
@Service
public class VerifierProfileContextFacadeImpl implements VerifierProfileContextFacade {

    private final VerifierProfileRepository profileRepository;
    private final VerificationCaseRepository caseRepository;

    public VerifierProfileContextFacadeImpl(VerifierProfileRepository profileRepository,
                                            VerificationCaseRepository caseRepository) {
        this.profileRepository = profileRepository;
        this.caseRepository = caseRepository;
    }

    @Override
    public boolean updateRating(int verifierUserId, double rating) {
        Optional<VerifierProfile> found = profileRepository.findByUserId(verifierUserId);
        if (found.isEmpty()) {
            return false;
        }

        // A revoked profile keeps receiving its rating: the history stays accurate if it is restored.
        profileRepository.save(found.get().updateRating(rating));
        return true;
    }

    @Override
    public boolean isEnabledVerifier(int userId) {
        return profileRepository.findByUserId(userId).map(VerifierProfile::isVerified).orElse(false);
    }

    @Override
    public List<VerifierWorkload> getAvailableVerifiers() {
        List<VerifierProfile> available = profileRepository.findEnabled().stream()
                .filter(profile -> profile.isVerified() && profile.isAvailable())
                .sorted(Comparator.comparingInt(VerifierProfile::getVerifierUserId))
                .toList();
        if (available.isEmpty()) {
            return List.of();
        }

        Map<Integer, Integer> openCases = caseRepository.countOpenByVerifierUserIds(
                available.stream().map(VerifierProfile::getVerifierUserId).toList());
        return available.stream()
                .map(profile -> new VerifierWorkload(profile.getVerifierUserId(),
                        openCases.getOrDefault(profile.getVerifierUserId(), 0)))
                .toList();
    }
}
