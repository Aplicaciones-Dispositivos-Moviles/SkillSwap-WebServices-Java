package com.innovify.skillswap.assessmentpeerreview.application.internal;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.services.VerifierCandidate;
import com.innovify.skillswap.assessmentpeerreview.domain.services.VerifierMatcher;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class CaseAssignmentServiceImpl implements CaseAssignmentService {

    private final VerifierProfileRepository profileRepository;
    private final VerificationCaseRepository caseRepository;
    private final VerifierMatcher verifierMatcher;

    public CaseAssignmentServiceImpl(VerifierProfileRepository profileRepository,
                                     VerificationCaseRepository caseRepository,
                                     VerifierMatcher verifierMatcher) {
        this.profileRepository = profileRepository;
        this.caseRepository = caseRepository;
        this.verifierMatcher = verifierMatcher;
    }

    @Override
    public boolean tryAssign(VerificationCase verificationCase) {
        List<VerifierProfile> profiles = profileRepository.findEnabledBySkillTag(verificationCase.getSkillTag());
        if (profiles.isEmpty()) {
            return false;
        }

        Map<Integer, Integer> openCases = caseRepository.countOpenByVerifierUserIds(
                profiles.stream().map(VerifierProfile::getVerifierUserId).toList());
        List<VerifierCandidate> candidates = profiles.stream()
                .map(profile -> new VerifierCandidate(profile,
                        openCases.getOrDefault(profile.getVerifierUserId(), 0)))
                .toList();

        // The parties of the case never review it: its student and, after an appeal, the first verifier.
        Set<Integer> excluded = new HashSet<>();
        excluded.add(verificationCase.getStudentId());
        if (verificationCase.getPreviousVerifierUserId() != null) {
            excluded.add(verificationCase.getPreviousVerifierUserId());
        }

        Optional<VerifierProfile> chosen = verifierMatcher.findVerifier(
                verificationCase.getSkillTag(), excluded, candidates);
        if (chosen.isEmpty()) {
            return false;
        }

        verificationCase.assignVerifier(chosen.get().getVerifierUserId());
        return true;
    }

    @Override
    public int assignPending(Iterable<String> skillTags) {
        Set<String> distinct = new LinkedHashSet<>();
        skillTags.forEach(distinct::add);

        int assigned = 0;
        for (String skillTag : distinct) {
            for (VerificationCase verificationCase : caseRepository.findPendingBySkillTag(skillTag)) {
                if (!tryAssign(verificationCase)) {
                    continue;
                }
                caseRepository.save(verificationCase);
                assigned++;
            }
        }
        return assigned;
    }
}
