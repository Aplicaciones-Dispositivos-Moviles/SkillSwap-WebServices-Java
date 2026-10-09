package com.innovify.skillswap.recognitionincentives.domain.repositories;

import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import java.util.Optional;

/** Persistence port of the {@link Wallet} aggregate. */
public interface WalletRepository {

    /** Persists a new or updated wallet and flushes right away. */
    Wallet save(Wallet wallet);

    Optional<Wallet> findByOwnerId(int ownerId);

    /**
     * Same as {@link #findByOwnerId(int)} but locks the row until the current transaction ends, so two
     * movements of the same wallet cannot read the same balance. It must run inside a transaction.
     */
    Optional<Wallet> findByOwnerIdForUpdate(int ownerId);
}
