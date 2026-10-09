package com.innovify.skillswap.recognitionincentives.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.RedemptionItem;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.ResolvedCaseType;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.TransactionType;
import com.innovify.skillswap.recognitionincentives.domain.services.CreditRewards;
import com.innovify.skillswap.recognitionincentives.domain.services.DefaultRedemptionPricing;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RecognitionIncentivesServicesTest {

    private final DefaultRedemptionPricing pricing = new DefaultRedemptionPricing();

    @Test
    void pricing_advancedPathUnlockCostsTwoHundred() {
        assertThat(pricing.calculateCost(RedemptionItem.ADVANCED_PATH_UNLOCK).value()).isEqualTo(200);
    }

    @Test
    void pricing_contributionCertificateCostsOneHundredTwenty() {
        assertThat(pricing.calculateCost(RedemptionItem.CONTRIBUTION_CERTIFICATE).value()).isEqualTo(120);
    }

    @Test
    void pricing_withoutItem_throwsDomainException() {
        assertThatThrownBy(() -> pricing.calculateCost(null)).isInstanceOf(DomainException.class);
    }

    @Test
    void rewards_aResolvedMiniProjectIsWorthForty() {
        assertThat(CreditRewards.forResolvedCase(ResolvedCaseType.MINI_PROJECT).value()).isEqualTo(40);
    }

    @Test
    void rewards_aResolvedQuizIsWorthTwentyFive() {
        assertThat(CreditRewards.forResolvedCase(ResolvedCaseType.QUIZ).value()).isEqualTo(25);
    }

    @Test
    void rewards_withoutCaseType_throwsDomainException() {
        assertThatThrownBy(() -> CreditRewards.forResolvedCase(null)).isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @CsvSource({"AdvancedPathUnlock,ADVANCED_PATH_UNLOCK", "contributioncertificate,CONTRIBUTION_CERTIFICATE",
            "' ContributionCertificate ',CONTRIBUTION_CERTIFICATE"})
    void redemptionItem_tryParse_isCaseInsensitive(String text, RedemptionItem expected) {
        assertThat(RedemptionItem.tryParse(text)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"coffee", "", "ADVANCED_PATH_UNLOCK"})
    void redemptionItem_tryParse_anythingElseIsEmpty(String text) {
        assertThat(RedemptionItem.tryParse(text)).isEmpty();
    }

    @Test
    void redemptionItem_tryParse_nullIsEmpty() {
        assertThat(RedemptionItem.tryParse(null)).isEmpty();
    }

    @Test
    void redemptionItem_exposesItsApiNameAndDescription() {
        assertThat(RedemptionItem.CONTRIBUTION_CERTIFICATE.value()).isEqualTo("ContributionCertificate");
        assertThat(RedemptionItem.CONTRIBUTION_CERTIFICATE.description()).isEqualTo("contribution certificate");
    }

    @Test
    void transactionType_fromValue_roundTrips() {
        for (TransactionType type : TransactionType.values()) {
            assertThat(TransactionType.fromValue(type.value())).isEqualTo(type);
        }
        assertThat(TransactionType.fromValue("earned")).isEqualTo(TransactionType.EARNED);
    }

    @Test
    void transactionType_fromValue_unknownThrows() {
        assertThatThrownBy(() -> TransactionType.fromValue("Gift")).isInstanceOf(IllegalArgumentException.class);
    }
}
