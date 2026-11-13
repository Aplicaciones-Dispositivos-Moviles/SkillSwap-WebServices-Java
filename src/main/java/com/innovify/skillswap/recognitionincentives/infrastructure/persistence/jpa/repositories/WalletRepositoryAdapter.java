package com.innovify.skillswap.recognitionincentives.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.repositories.WalletRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link WalletRepository} port on top of Spring Data JPA. */
@Repository
public class WalletRepositoryAdapter implements WalletRepository {

    private final WalletJpaRepository jpaRepository;

    public WalletRepositoryAdapter(WalletJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Wallet save(Wallet wallet) {
        return jpaRepository.saveAndFlush(wallet);
    }

    @Override
    public Optional<Wallet> findByOwnerId(int ownerId) {
        return jpaRepository.findByWalletOwnerId(ownerId);
    }

    @Override
    public Optional<Wallet> findByOwnerIdForUpdate(int ownerId) {
        return jpaRepository.findForUpdateByWalletOwnerId(ownerId);
    }
}
