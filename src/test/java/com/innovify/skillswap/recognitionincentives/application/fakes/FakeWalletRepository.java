package com.innovify.skillswap.recognitionincentives.application.fakes;

import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.repositories.WalletRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository that assigns ids like the database does. */
public class FakeWalletRepository implements WalletRepository {

    private final List<Wallet> items = new ArrayList<>();
    private int nextId = 1;
    private int saveCalls;
    private int lockedReads;
    private RuntimeException saveFailure;

    public List<Wallet> items() {
        return items;
    }

    public int saveCalls() {
        return saveCalls;
    }

    /** How many times a wallet was read with a lock. */
    public int lockedReads() {
        return lockedReads;
    }

    /** Makes every following save throw the given exception. */
    public void failOnSave(RuntimeException failure) {
        this.saveFailure = failure;
    }

    @Override
    public Wallet save(Wallet wallet) {
        saveCalls++;
        if (saveFailure != null) {
            throw saveFailure;
        }
        if (wallet.getId() == null) {
            ReflectionTestUtils.setField(wallet, "id", nextId++);
        }
        if (!items.contains(wallet)) {
            items.add(wallet);
        }
        return wallet;
    }

    @Override
    public Optional<Wallet> findByOwnerId(int ownerId) {
        return items.stream().filter(w -> w.getWalletOwnerId() == ownerId).findFirst();
    }

    @Override
    public Optional<Wallet> findByOwnerIdForUpdate(int ownerId) {
        lockedReads++;
        return findByOwnerId(ownerId);
    }
}
