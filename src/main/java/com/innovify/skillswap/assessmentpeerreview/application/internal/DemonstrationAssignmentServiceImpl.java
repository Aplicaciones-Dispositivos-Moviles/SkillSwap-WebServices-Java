package com.innovify.skillswap.assessmentpeerreview.application.internal;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.FinalDemonstrationRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.services.VerifierCandidate;
import com.innovify.skillswap.assessmentpeerreview.domain.services.VerifierMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DemonstrationAssignmentServiceImpl implements DemonstrationAssignmentService {

    private record Choice(VerifierProfile profile, String skillTag, int load) {
    }

    private final VerifierProfileRepository profileRepository;
    private final VerificationCaseRepository caseRepository;
    private final FinalDemonstrationRepository demonstrationRepository;
    private final VerifierMatcher verifierMatcher;

    public DemonstrationAssignmentServiceImpl(VerifierProfileRepository profileRepository,
                                              VerificationCaseRepository caseRepository,
                                              FinalDemonstrationRepository demonstrationRepository,
                                              VerifierMatcher verifierMatcher) {
        this.profileRepository = profileRepository;
        this.caseRepository = caseRepository;
        this.demonstrationRepository = demonstrationRepository;
        this.verifierMatcher = verifierMatcher;
    }

    @Override
    public Optional<DemonstrationAssignment> findVerifier(int studentId, Collection<String> skillTags) {
        Set<String> distinct = new LinkedHashSet<>(skillTags);
        List<Choice> choices = new ArrayList<>();

        for (String skillTag : distinct) {
            List<VerifierProfile> profiles = profileRepository.findEnabledBySkillTag(skillTag);
            if (profiles.isEmpty()) {
                continue;
            }

            List<Integer> ids = profiles.stream().map(VerifierProfile::getVerifierUserId).toList();
            Map<Integer, Integer> openCases = caseRepository.countOpenByVerifierUserIds(ids);
            Map<Integer, Integer> activeDemonstrations = demonstrationRepository.countActiveByVerifierUserIds(ids);
            List<VerifierCandidate> candidates = profiles.stream()
                    .map(profile -> new VerifierCandidate(profile,
                            openCases.getOrDefault(profile.getVerifierUserId(), 0)
                                    + activeDemonstrations.getOrDefault(profile.getVerifierUserId(), 0)))
                    .toList();

            verifierMatcher.findVerifier(skillTag, studentId, candidates).ifPresent(chosen -> choices.add(
                    new Choice(chosen, skillTag, candidates.stream()
                            .filter(candidate -> candidate.profile() == chosen)
                            .mapToInt(VerifierCandidate::openCaseCount)
                            .findFirst()
                            .orElse(0))));
        }

        return choices.stream()
                .min(Comparator.comparingInt(Choice::load)
                        .thenComparingInt(choice -> choice.profile().getVerifierUserId()))
                .map(choice -> new DemonstrationAssignment(choice.profile().getVerifierUserId(), choice.skillTag()));
    }
}
