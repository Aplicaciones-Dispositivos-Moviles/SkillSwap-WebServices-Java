package com.innovify.skillswap.recognitionincentives.application.fakes;

import com.innovify.skillswap.recognitionincentives.domain.model.entities.RedemptionIdempotencyKey;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.IdempotencyKey;
import com.innovify.skillswap.recognitionincentives.domain.repositories.RedemptionIdempotencyKeyRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * In-memory repository that assigns ids and enforces the unique user + key like the database does. It can also
 * simulate another request that stores the same key between the lookup and the save.
 */
public class FakeRedemptionIdempotencyKeyRepository implements RedemptionIdempotencyKeyRepository {

    private final List<RedemptionIdempotencyKey> items = new ArrayList<>();
    private int nextId = 1;
    private RedemptionIdempotencyKey storedByAnotherRequest;

    /** The keys in the order they were saved. */
    public List<RedemptionIdempotencyKey> items() {
        return items;
    }

    /** The next save finds this key already stored by another request, and fails like the unique constraint. */
    public void storeFirstOnNextSave(RedemptionIdempotencyKey winner) {
        this.storedByAnotherRequest = winner;
    }

    @Override
    public RedemptionIdempotencyKey save(RedemptionIdempotencyKey key) {
        if (storedByAnotherRequest != null) {
            add(storedByAnotherRequest);
            storedByAnotherRequest = null;
        }
        if (findByUserIdAndKey(key.getUserId(), key.getKey()).isPresent()) {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
        }
        return add(key);
    }

    @Override
    public Optional<RedemptionIdempotencyKey> findByUserIdAndKey(int userId, IdempotencyKey key) {
        return items.stream().filter(k -> k.getUserId() == userId && k.getKey().equals(key)).findFirst();
    }

    private RedemptionIdempotencyKey add(RedemptionIdempotencyKey key) {
        if (key.getId() == null) {
            ReflectionTestUtils.setField(key, "id", nextId++);
        }
        items.add(key);
        return key;
    }
}
