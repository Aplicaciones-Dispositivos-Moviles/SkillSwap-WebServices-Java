package com.innovify.skillswap.recognitionincentives.application.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.domain.model.events.VerificationCaseResolved;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.iam.domain.model.events.UserRegistered;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.recognitionincentives.application.commandservices.WalletCommandService;
import com.innovify.skillswap.recognitionincentives.domain.model.RecognitionIncentivesError;
import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.CreateWalletCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.CreditVerifierCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.RedeemCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.ResolvedCaseType;
import com.innovify.skillswap.shared.application.Result;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class RecognitionIncentivesEventHandlersTest {

    private final RecordingWalletCommandService service = new RecordingWalletCommandService();

    @Test
    void userRegistered_becomesTheCreationOfTheWallet() {
        new CreateWalletEventHandler(service).handle(new UserRegistered(4, UserRole.STUDENT));

        assertThat(service.creations).containsExactly(new CreateWalletCommand(4));
        assertThat(service.credits).isEmpty();
    }

    @Test
    void userRegistered_whenTheServiceFails_doesNotThrow() {
        service.fail = true;

        new CreateWalletEventHandler(service).handle(new UserRegistered(4, UserRole.STUDENT));

        assertThat(service.creations).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(ReviewDecision.class)
    void caseResolved_creditsTheVerifierWhateverTheDecision(ReviewDecision decision) {
        new CreditVerifierEventHandler(service).handle(
                new VerificationCaseResolved(10, 1, 2, 5, "http-basics", CaseType.QUIZ, decision, null));

        assertThat(service.credits).containsExactly(new CreditVerifierCommand(2, 10, ResolvedCaseType.QUIZ));
        assertThat(service.creations).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"QUIZ,QUIZ", "MINI_PROJECT,MINI_PROJECT"})
    void caseResolved_translatesTheTypeOfTheCase(CaseType caseType, ResolvedCaseType expected) {
        new CreditVerifierEventHandler(service).handle(
                new VerificationCaseResolved(10, 1, 2, 5, "http-basics", caseType, ReviewDecision.APPROVED, null));

        assertThat(service.credits).containsExactly(new CreditVerifierCommand(2, 10, expected));
    }

    @Test
    void caseResolved_whenTheServiceFails_doesNotThrow() {
        service.fail = true;

        new CreditVerifierEventHandler(service).handle(
                new VerificationCaseResolved(10, 1, 2, 5, "http-basics", CaseType.QUIZ, ReviewDecision.APPROVED, null));

        assertThat(service.credits).hasSize(1);
    }

    private static final class RecordingWalletCommandService implements WalletCommandService {

        final List<CreateWalletCommand> creations = new ArrayList<>();
        final List<CreditVerifierCommand> credits = new ArrayList<>();
        boolean fail;

        private <T> Result<T> answer(T value) {
            return fail ? Result.failure(RecognitionIncentivesError.DATABASE_ERROR, "boom") : Result.success(value);
        }

        @Override
        public Result<Wallet> handle(CreateWalletCommand command) {
            creations.add(command);
            return answer(new Wallet(command.ownerId()));
        }

        @Override
        public Result<Wallet> handle(CreditVerifierCommand command) {
            credits.add(command);
            return answer(new Wallet(command.verifierUserId()));
        }

        @Override
        public Result<CreditTransaction> handle(RedeemCommand command) {
            throw new UnsupportedOperationException();
        }
    }
}
