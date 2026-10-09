package com.innovify.skillswap.recognitionincentives.application.fakes;

import com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.RedemptionItem;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.TransactionType;
import com.innovify.skillswap.recognitionincentives.domain.repositories.CreditTransactionRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository that assigns ids like the database does. */
public class FakeCreditTransactionRepository implements CreditTransactionRepository {

    private final List<CreditTransaction> items = new ArrayList<>();
    private int nextId = 1;
    private RuntimeException saveFailure;

    /** The movements in the order they were saved. */
    public List<CreditTransaction> items() {
        return items;
    }

    /** Makes every following save throw the given exception. */
    public void failOnSave(RuntimeException failure) {
        this.saveFailure = failure;
    }

    @Override
    public CreditTransaction save(CreditTransaction transaction) {
        if (saveFailure != null) {
            throw saveFailure;
        }
        if (transaction.getId() == null) {
            ReflectionTestUtils.setField(transaction, "id", nextId++);
        }
        items.add(transaction);
        return transaction;
    }

    @Override
    public List<CreditTransaction> findByWalletId(int walletId) {
        List<CreditTransaction> result = new ArrayList<>(
                items.stream().filter(t -> t.getWalletId() == walletId).toList());
        java.util.Collections.reverse(result);
        return result;
    }

    @Override
    public boolean existsEarnedForCase(int walletId, int caseId) {
        return items.stream().anyMatch(t -> t.getWalletId() == walletId
                && t.getType() == TransactionType.EARNED
                && t.getRelatedCaseId() != null && t.getRelatedCaseId() == caseId);
    }

    @Override
    public List<Integer> findRedemptionIds(int walletId, RedemptionItem item) {
        return items.stream()
                .filter(t -> t.getWalletId() == walletId && t.getRedemptionItem() == item)
                .map(CreditTransaction::getId)
                .sorted()
                .toList();
    }
}
