package com.innovify.skillswap.recognitionincentives.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.recognitionincentives.application.queryservices.PublicCredentialView;
import com.innovify.skillswap.recognitionincentives.application.queryservices.ShareableCredentialView;
import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.TransactionType;
import com.innovify.skillswap.recognitionincentives.interfaces.rest.transform.RecognitionIncentivesResourceAssemblers;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RecognitionIncentivesResourceAssemblersTest {

    @Test
    void wallet_isConvertedWithItsOwnerAndBalance() {
        Wallet wallet = new Wallet(3).credit(new Credits(40));
        ReflectionTestUtils.setField(wallet, "id", 5);

        var resource = RecognitionIncentivesResourceAssemblers.toResource(wallet);

        assertThat(resource.id()).isEqualTo(5);
        assertThat(resource.walletOwnerId()).isEqualTo(3);
        assertThat(resource.balance()).isEqualTo(40);
    }

    @Test
    void transaction_isConvertedWithTheNameOfItsType() {
        CreditTransaction transaction = new CreditTransaction(5, new Credits(10), TransactionType.EARNED,
                "Verification case resolved", 7);
        ReflectionTestUtils.setField(transaction, "id", 9);

        var resource = RecognitionIncentivesResourceAssemblers.toResource(transaction);

        assertThat(resource.id()).isEqualTo(9);
        assertThat(resource.walletId()).isEqualTo(5);
        assertThat(resource.amount()).isEqualTo(10);
        assertThat(resource.type()).isEqualTo("Earned");
        assertThat(resource.description()).isEqualTo("Verification case resolved");
        assertThat(resource.relatedCaseId()).isEqualTo(7);
        assertThat(resource.createdAt()).isEqualTo(transaction.getCreatedAt());
    }

    @Test
    void redeemedTransaction_hasNoCase() {
        CreditTransaction transaction = new CreditTransaction(5, new Credits(30), TransactionType.REDEEMED,
                "Redeemed: contribution certificate", null);
        ReflectionTestUtils.setField(transaction, "id", 9);

        var resource = RecognitionIncentivesResourceAssemblers.toResource(transaction);

        assertThat(resource.type()).isEqualTo("Redeemed");
        assertThat(resource.relatedCaseId()).isNull();
    }

    @Test
    void toResource_ofACredentialView_copiesEveryField() {
        Instant createdAt = Instant.parse("2026-10-22T10:00:00Z");

        var resource = RecognitionIncentivesResourceAssemblers.toResource(new ShareableCredentialView(
                "java-basics", "Java Basics", "token", "https://x/y", "text", createdAt));

        assertThat(resource.skillTag()).isEqualTo("java-basics");
        assertThat(resource.skillName()).isEqualTo("Java Basics");
        assertThat(resource.token()).isEqualTo("token");
        assertThat(resource.url()).isEqualTo("https://x/y");
        assertThat(resource.shareText()).isEqualTo("text");
        assertThat(resource.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void toResource_ofAPublicCredentialView_copiesEveryField() {
        Instant issuedAt = Instant.parse("2026-10-22T10:00:00Z");

        var resource = RecognitionIncentivesResourceAssemblers.toResource(new PublicCredentialView(
                "Ana Torres", "java-basics", "Java Basics", 75, issuedAt));

        assertThat(resource.fullName()).isEqualTo("Ana Torres");
        assertThat(resource.skillTag()).isEqualTo("java-basics");
        assertThat(resource.skillName()).isEqualTo("Java Basics");
        assertThat(resource.skillCredits()).isEqualTo(75);
        assertThat(resource.issuedAt()).isEqualTo(issuedAt);
    }
}
