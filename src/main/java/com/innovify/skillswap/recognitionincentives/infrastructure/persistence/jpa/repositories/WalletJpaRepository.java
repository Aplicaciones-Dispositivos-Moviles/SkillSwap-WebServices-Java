package com.innovify.skillswap.recognitionincentives.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Spring Data access to the "wallets" table. Only {@link WalletRepositoryAdapter} uses it. */
public interface WalletJpaRepository extends JpaRepository<Wallet, Integer> {

    Optional<Wallet> findByWalletOwnerId(int ownerId);

    /** SELECT ... FOR UPDATE: it waits for the other transactions that hold the wallet. Needs a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Wallet> findForUpdateByWalletOwnerId(int ownerId);
}
