package com.innovify.skillswap.assessmentpeerreview.application.fakes;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository that assigns ids like the database does. */
public class FakeVerifierProfileRepository implements VerifierProfileRepository {

    private final List<VerifierProfile> profiles = new ArrayList<>();
    private int nextId = 1;
    private int saveCalls;
    private RuntimeException saveFailure;

    public List<VerifierProfile> profiles() {
        return profiles;
    }

    public int saveCalls() {
        return saveCalls;
    }

    /** Makes every following save throw the given exception. */
    public void failOnSave(RuntimeException failure) {
        this.saveFailure = failure;
    }

    @Override
    public VerifierProfile save(VerifierProfile profile) {
        saveCalls++;
        if (saveFailure != null) {
            throw saveFailure;
        }
        if (profile.getId() == null) {
            ReflectionTestUtils.setField(profile, "id", nextId++);
        }
        if (!profiles.contains(profile)) {
            profiles.add(profile);
        }
        return profile;
    }

    @Override
    public Optional<VerifierProfile> findByUserId(int userId) {
        return profiles.stream().filter(p -> p.getVerifierUserId() == userId).findFirst();
    }

    @Override
    public List<VerifierProfile> findEnabledBySkillTag(String skillTag) {
        return profiles.stream().filter(p -> p.canReview(skillTag)).toList();
    }
}
