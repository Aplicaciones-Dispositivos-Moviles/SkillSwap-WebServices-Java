package com.innovify.skillswap.recognitionincentives.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.TransactionType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the "credit_transactions" table. Only {@link CreditTransactionRepositoryAdapter} uses it. */
public interface CreditTransactionJpaRepository extends JpaRepository<CreditTransaction, Integer> {

    List<CreditTransaction> findByWalletIdOrderByCreatedAtDescIdDesc(int walletId);

    boolean existsByWalletIdAndTypeAndRelatedCaseId(int walletId, TransactionType type, int relatedCaseId);
}
