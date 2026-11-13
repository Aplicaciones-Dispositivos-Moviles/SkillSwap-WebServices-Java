package com.innovify.skillswap.recognitionincentives.application.fakes;

import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.ShareableCredential;
import com.innovify.skillswap.recognitionincentives.domain.repositories.ShareableCredentialRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository that assigns ids and refuses repeated (owner, skill) and tokens like the database does. */
public class FakeShareableCredentialRepository implements ShareableCredentialRepository {

    private final List<ShareableCredential> items = new ArrayList<>();
    private int nextId = 1;
    private int saveCalls;
    private RuntimeException saveFailure;
    private Runnable beforeSave;

    public List<ShareableCredential> items() {
        return items;
    }

    public int saveCalls() {
        return saveCalls;
    }

    /** Makes every following save throw the given exception. */
    public void failOnSave(RuntimeException failure) {
        this.saveFailure = failure;
    }

    /** Runs the action right before the next saves, to simulate another request that wins the race. */
    public void beforeSave(Runnable action) {
        this.beforeSave = action;
    }

    @Override
    public ShareableCredential save(ShareableCredential credential) {
        saveCalls++;
        if (beforeSave != null) {
            Runnable action = beforeSave;
            beforeSave = null;
            action.run();
        }
        if (saveFailure != null) {
            throw saveFailure;
        }
        boolean repeated = items.stream().anyMatch(other -> other != credential
                && (other.getToken().equals(credential.getToken())
                || (other.getOwnerId() == credential.getOwnerId()
                && other.getSkillTag().equals(credential.getSkillTag()))));
        if (repeated) {
            throw new DataIntegrityViolationException("unique");
        }
        if (credential.getId() == null) {
            ReflectionTestUtils.setField(credential, "id", nextId++);
        }
        if (!items.contains(credential)) {
            items.add(credential);
        }
        return credential;
    }

    @Override
    public Optional<ShareableCredential> findByToken(String token) {
        return items.stream().filter(c -> c.getToken().equals(token)).findFirst();
    }

    @Override
    public Optional<ShareableCredential> findByOwnerIdAndSkillTag(int ownerId, String skillTag) {
        return items.stream().filter(c -> c.getOwnerId() == ownerId && c.getSkillTag().equals(skillTag)).findFirst();
    }

    @Override
    public List<ShareableCredential> findByOwnerId(int ownerId) {
        return items.stream().filter(c -> c.getOwnerId() == ownerId)
                .sorted(Comparator.comparing(ShareableCredential::getCreatedAt).reversed()
                        .thenComparing(c -> -c.getId()))
                .toList();
    }
}
