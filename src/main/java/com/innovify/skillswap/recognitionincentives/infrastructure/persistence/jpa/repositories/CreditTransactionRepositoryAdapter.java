package com.innovify.skillswap.recognitionincentives.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.TransactionType;
import com.innovify.skillswap.recognitionincentives.domain.repositories.CreditTransactionRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link CreditTransactionRepository} port on top of Spring Data JPA. */
@Repository
public class CreditTransactionRepositoryAdapter implements CreditTransactionRepository {

    private final CreditTransactionJpaRepository jpaRepository;

    public CreditTransactionRepositoryAdapter(CreditTransactionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CreditTransaction save(CreditTransaction transaction) {
        return jpaRepository.saveAndFlush(transaction);
    }

    @Override
    public List<CreditTransaction> findByWalletId(int walletId) {
        return jpaRepository.findByWalletIdOrderByCreatedAtDescIdDesc(walletId);
    }

    @Override
    public boolean existsEarnedForCase(int walletId, int caseId) {
        return jpaRepository.existsByWalletIdAndTypeAndRelatedCaseId(walletId, TransactionType.EARNED, caseId);
    }
}
