package com.innovify.skillswap.moderationdisputes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.moderationdisputes.domain.model.aggregates.Dispute;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeOutcome;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeSourceType;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeStatus;
import com.innovify.skillswap.moderationdisputes.domain.services.DisputeResolutionValidator;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.util.List;
import org.junit.jupiter.api.Test;

class DisputeTest {

    private final DisputeResolutionValidator validator = new DisputeResolutionValidator();

    @Test
    void certificateReview_isOpenedByTheSystemAgainstTheOwner() {
        Dispute dispute = Dispute.certificateReview(10, 7, List.of("DuplicateFile", "HolderNameMismatch"));

        assertThat(dispute.getSourceType()).isEqualTo(DisputeSourceType.CERTIFICATE_REVIEW);
        assertThat(dispute.getSourceReferenceId()).isEqualTo(10);
        assertThat(dispute.getRespondentUserId()).isEqualTo(7);
        assertThat(dispute.getRaisedByUserId()).isNull();
        assertThat(dispute.getReason()).isEqualTo("DuplicateFile, HolderNameMismatch");
        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.PENDING);
        assertThat(dispute.hasReviewer()).isFalse();
        assertThat(dispute.getRaisedAt()).isNotNull();
        assertThat(Dispute.certificateReview(10, 7, List.of()).getReason()).isEqualTo("HighRisk");
    }

    @Test
    void constructor_rejectsInvalidData() {
        assertThatThrownBy(() -> new Dispute(null, 1, null, 2, "x")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Dispute(DisputeSourceType.CERTIFICATE_REVIEW, 0, null, 2, "x"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Dispute(DisputeSourceType.CERTIFICATE_REVIEW, 1, null, -2, "x"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Dispute(DisputeSourceType.CERTIFICATE_REVIEW, 1, null, 2, " "))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Dispute(DisputeSourceType.CERTIFICATE_REVIEW, 1, null, 2, "x".repeat(501)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void assignReviewer_neverGivesTheDisputeToItsParties() {
        Dispute dispute = new Dispute(DisputeSourceType.CERTIFICATE_REVIEW, 1, 3, 2, "x");

        assertThatThrownBy(() -> dispute.assignReviewer(2, true)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> dispute.assignReviewer(3, true)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> dispute.assignReviewer(0, true)).isInstanceOf(DomainException.class);

        dispute.assignReviewer(5, false);
        assertThat(dispute.isAssignedTo(5)).isTrue();
        assertThat(dispute.isAssignedToSenior()).isFalse();
        assertThat(dispute.getAssignedAt()).isNotNull();
        assertThatThrownBy(() -> dispute.assignReviewer(6, true)).isInstanceOf(DomainException.class);
    }

    @Test
    void resolve_recordsTheDecisionOfTheReviewer() {
        Dispute dispute = Dispute.certificateReview(1, 2, List.of("DuplicateFile")).assignReviewer(5, true);

        dispute.resolve(DisputeOutcome.UPHELD, "  Legitimate.  ", validator);

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(dispute.getOutcome()).isEqualTo(DisputeOutcome.UPHELD);
        assertThat(dispute.getCoordinatorNotes()).isEqualTo("Legitimate.");
        assertThat(dispute.getResolvedAt()).isNotNull();
        assertThatThrownBy(() -> dispute.resolve(DisputeOutcome.UPHELD, "Again", validator))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> dispute.assignReviewer(6, true)).isInstanceOf(DomainException.class);
    }

    @Test
    void resolve_rejectsAMissingReviewerAWrongOutcomeAndMissingNotes() {
        Dispute unassigned = Dispute.certificateReview(1, 2, List.of());
        assertThatThrownBy(() -> unassigned.resolve(DisputeOutcome.UPHELD, "ok", validator))
                .isInstanceOf(DomainException.class);

        Dispute dispute = Dispute.certificateReview(1, 2, List.of()).assignReviewer(5, true);
        assertThatThrownBy(() -> dispute.resolve(DisputeOutcome.SANCTIONED, "ok", validator))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> dispute.resolve(null, "ok", validator)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> dispute.resolve(DisputeOutcome.OVERTURNED, " ", validator))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> dispute.resolve(DisputeOutcome.OVERTURNED, "x".repeat(2001), validator))
                .isInstanceOf(DomainException.class);
        assertThat(dispute.isPending()).isTrue();
    }

    @Test
    void validator_acceptsOnlyTheOutcomesOfEachOrigin() {
        assertThat(validator.isValidOutcome(DisputeSourceType.CERTIFICATE_REVIEW, DisputeOutcome.UPHELD)).isTrue();
        assertThat(validator.isValidOutcome(DisputeSourceType.CERTIFICATE_REVIEW, DisputeOutcome.OVERTURNED)).isTrue();
        assertThat(validator.isValidOutcome(DisputeSourceType.CERTIFICATE_REVIEW, DisputeOutcome.DISMISSED)).isFalse();
        assertThat(validator.isValidOutcome(DisputeSourceType.CERTIFICATE_REVIEW, DisputeOutcome.SANCTIONED)).isFalse();
        assertThat(validator.isValidOutcome(DisputeSourceType.VERIFIER_DECISION_APPEAL, DisputeOutcome.OVERTURNED))
                .isTrue();
        assertThat(validator.isValidOutcome(DisputeSourceType.USER_REPORT, DisputeOutcome.SANCTIONED)).isTrue();
        assertThat(validator.isValidOutcome(DisputeSourceType.USER_REPORT, DisputeOutcome.UPHELD)).isFalse();
        assertThat(validator.isValidOutcome(null, DisputeOutcome.UPHELD)).isFalse();
        assertThat(validator.canResolve(null)).isFalse();
    }
}
