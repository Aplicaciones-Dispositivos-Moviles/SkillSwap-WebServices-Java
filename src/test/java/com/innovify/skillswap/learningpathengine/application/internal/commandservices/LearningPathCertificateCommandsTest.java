package com.innovify.skillswap.learningpathengine.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.credentialverification.application.acl.CertificateSummary;
import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.application.commandservices.CertificateLinkOutcome;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeCertificateSkillAffinityScorer;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeAdvancedPathUnlockRepository;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeCredentialContextFacade;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeRecognitionContextFacade;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeLearningPathRepository;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeSkillTaxonomyMatcher;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeSubscriptionContextFacade;
import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.commands.CompletePathNodeCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.DeclareGoalCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.LinkCertificateToNodeCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.PauseLearningPathCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.RecognizeValidatedCertificateCommand;
import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.SkillAffinity;
import com.innovify.skillswap.learningpathengine.domain.services.DefaultLearningPathBuilder;
import com.innovify.skillswap.learningpathengine.domain.services.DefaultSkillGapAnalyzer;
import com.innovify.skillswap.shared.application.Result;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Certificates and the learning path: validated certificates recognized as demonstrated skills (US09) and the
 * certificate-skill correspondence when the student associates a certificate with a node (US15). The fake scorer
 * gives 1.0 when the course name matches the skill ("REST", "HTTP", "JWT", "SQL"), 0.75 when only the OCR text
 * does, and 0 otherwise.
 */
class LearningPathCertificateCommandsTest {

    private static final String REST_AND_JWT = "I want to build REST APIs with JWT";

    private final FakeCredentialContextFacade credentials = new FakeCredentialContextFacade();
    private final FakeLearningPathRepository paths = new FakeLearningPathRepository();
    private LearningPathCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        LocaleContextHolder.setLocale(Locale.US);
        service = new LearningPathCommandServiceImpl(paths, FakeSkillTaxonomyMatcher.sample(),
                FakeCertificateSkillAffinityScorer.sample(),
                new DefaultSkillGapAnalyzer(TestData.TAXONOMY), new DefaultLearningPathBuilder(TestData.TAXONOMY),
                credentials, new FakeSubscriptionContextFacade(), new FakeAdvancedPathUnlockRepository(),
                new FakeRecognitionContextFacade(), TransactionOperations.withoutTransaction(), messages);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private Result<LearningPath> declare(String goal) {
        return service.handle(new DeclareGoalCommand(1, goal));
    }

    private Result<CertificateLinkOutcome> link(int nodeId, int certificateId, int studentId) {
        return service.handle(new LinkCertificateToNodeCommand(nodeId, studentId, certificateId));
    }

    private Result<CertificateLinkOutcome> link(int nodeId, int certificateId) {
        return link(nodeId, certificateId, 1);
    }

    private Result<List<LearningPath>> recognize(int certificateId) {
        return service.handle(new RecognizeValidatedCertificateCommand(1, certificateId));
    }

    private static PathNode node(LearningPath path, String skillTag) {
        return path.getNodes().stream().filter(n -> n.getSkillTag().equals(skillTag)).findFirst().orElseThrow();
    }

    private static void assertFailure(Result<?> result, LearningPathError expected) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(expected);
        assertThat(result.message()).isNotBlank().isNotEqualTo(expected.name());
    }

    // ---------- US09 escenario 1: the path counts the validated certificates ----------

    @Test
    @DisplayName("US09 escenario 1: the node of a skill covered by a validated certificate is completed and linked")
    void declare_withAValidatedCertificate_completesTheNodeOfItsSkillAndLinksIt() {
        credentials.addValidated(40, 1, "REST fundamentals", "");

        LearningPath path = declare(REST_AND_JWT).value();

        // The certified skill stops the walk: its prerequisites (http, networking, programming) are not required.
        assertThat(path.getNodes()).extracting(PathNode::getSkillTag)
                .containsExactly("rest-api-design", "authentication-jwt");
        PathNode rest = node(path, "rest-api-design");
        assertThat(rest.getStatus()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(rest.getLinkedCertificateId()).isEqualTo(40);
        assertThat(rest.isCompletedByCertificate()).isTrue();
        assertThat(node(path, "authentication-jwt").getStatus()).isEqualTo(NodeStatus.AVAILABLE);
        assertThat(path.getStatus()).isEqualTo(PathStatus.ACTIVE);
        assertThat(paths.paths()).containsExactly(path);
    }

    @Test
    void declare_withAValidatedCertificateThatCoversAPrerequisite_completesThatNode() {
        credentials.addValidated(40, 1, "HTTP essentials", "");

        LearningPath path = declare(REST_AND_JWT).value();

        assertThat(path.getNodes()).extracting(PathNode::getSkillTag).containsExactly("http-basics",
                "programming-fundamentals", "rest-api-design", "authentication-jwt");
        assertThat(path.getNodes()).extracting(PathNode::getStatus).containsExactly(NodeStatus.COMPLETED,
                NodeStatus.AVAILABLE, NodeStatus.LOCKED, NodeStatus.LOCKED);
        assertThat(node(path, "http-basics").getLinkedCertificateId()).isEqualTo(40);
    }

    @Test
    void declare_comparesTheOcrTextOfTheValidatedCertificate() {
        credentials.addValidated(40, 1, "Backend bootcamp", "Topics: designing REST services");

        LearningPath path = declare(REST_AND_JWT).value();

        assertThat(node(path, "rest-api-design").isCompletedByCertificate()).isTrue();
    }

    @Test
    void declare_withSeveralValidatedCertificatesForASkill_linksTheOldest() {
        credentials.addValidated(41, 1, "Advanced REST", "");
        credentials.addValidated(40, 1, "REST basics", "");

        LearningPath path = declare(REST_AND_JWT).value();

        assertThat(node(path, "rest-api-design").getLinkedCertificateId()).isEqualTo(40);
    }

    @Test
    void declare_withACertificateThatIsNotValidated_onlyLinksItAsEvidence() {
        credentials.addUnverified(40, 1, "REST fundamentals", "");
        credentials.certificates().add(new CertificateSummary(40, "REST fundamentals", null));

        LearningPath path = declare(REST_AND_JWT).value();

        PathNode rest = node(path, "rest-api-design");
        assertThat(rest.getStatus()).isEqualTo(NodeStatus.LOCKED);
        assertThat(rest.getLinkedCertificateId()).isEqualTo(40);
        assertThat(rest.isCompletedByCertificate()).isFalse();
        assertThat(path.getNodes()).hasSize(5);
    }

    @Test
    void declare_ignoresTheValidatedCertificatesOfOtherStudents() {
        credentials.addValidated(40, 2, "REST fundamentals", "");

        LearningPath path = declare(REST_AND_JWT).value();

        assertThat(path.getNodes()).extracting(PathNode::getStatus).doesNotContain(NodeStatus.COMPLETED);
    }

    @Test
    void declare_whenValidatedCertificatesCoverTheWholeGoal_returnsGoalAlreadyAchieved() {
        credentials.addValidated(40, 1, "REST and JWT security", "");

        assertFailure(declare(REST_AND_JWT), LearningPathError.GOAL_ALREADY_ACHIEVED);
        assertThat(paths.paths()).isEmpty();
    }

    @Test
    void declare_whenTheCertificatesCannotBeRead_createsThePathWithoutRecognizingAny() {
        credentials.addValidated(40, 1, "REST fundamentals", "");
        credentials.failWith(new IllegalStateException("credential context unavailable"));

        Result<LearningPath> result = declare(REST_AND_JWT);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getNodes()).hasSize(5);
        assertThat(result.value().getNodes()).extracting(PathNode::getStatus).doesNotContain(NodeStatus.COMPLETED);
    }

    // ---------- US09 escenario 2: a certificate validated later ----------

    @Test
    @DisplayName("US09 escenario 2: a new validated certificate completes the pending node and keeps the completed ones")
    void recognize_completesThePendingNodeItCoversAndKeepsTheCompletedOnes() {
        LearningPath path = declare(REST_AND_JWT).value();
        service.handle(new CompletePathNodeCommand(node(path, "networking-basics").getId()));
        credentials.addValidated(60, 1, "HTTP essentials", "");

        Result<List<LearningPath>> result = recognize(60);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).containsExactly(path);
        PathNode http = node(path, "http-basics");
        assertThat(http.getStatus()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(http.isCompletedByCertificate()).isTrue();
        assertThat(http.getLinkedCertificateId()).isEqualTo(60);
        PathNode networking = node(path, "networking-basics");
        assertThat(networking.getStatus()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(networking.isCompletedByCertificate()).isFalse();
        assertThat(networking.getLinkedCertificateId()).isNull();
        // rest-api-design still needs programming-fundamentals.
        assertThat(node(path, "rest-api-design").getStatus()).isEqualTo(NodeStatus.LOCKED);
    }

    @Test
    void recognize_unlocksTheNodesThatOnlyNeededTheCertifiedSkill() {
        LearningPath path = declare(REST_AND_JWT).value();
        credentials.addValidated(60, 1, "REST in practice", "");

        recognize(60);

        assertThat(node(path, "rest-api-design").getStatus()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(node(path, "authentication-jwt").getStatus()).isEqualTo(NodeStatus.AVAILABLE);
    }

    @Test
    void recognize_alsoUpdatesAPausedPath() {
        LearningPath path = declare(REST_AND_JWT).value();
        service.handle(new PauseLearningPathCommand(path.getId(), 1));
        credentials.addValidated(60, 1, "HTTP essentials", "");

        assertThat(recognize(60).value()).containsExactly(path);
        assertThat(path.getStatus()).isEqualTo(PathStatus.PAUSED);
        assertThat(node(path, "http-basics").getStatus()).isEqualTo(NodeStatus.COMPLETED);
    }

    @Test
    void recognize_whenTheCertificateCoversNoPendingNode_changesNothing() {
        declare(REST_AND_JWT);
        credentials.addValidated(60, 1, "SQL for everyone", "");
        int savesBefore = paths.saveCalls();

        Result<List<LearningPath>> result = recognize(60);

        assertThat(result.value()).isEmpty();
        assertThat(paths.saveCalls()).isEqualTo(savesBefore);
    }

    @Test
    void recognize_twice_changesNothingTheSecondTime() {
        declare(REST_AND_JWT);
        credentials.addValidated(60, 1, "HTTP essentials", "");
        recognize(60);

        assertThat(recognize(60).value()).isEmpty();
    }

    @Test
    void recognize_skipsTheCompletedPaths() {
        LearningPath path = declare("I want to learn HTTP").value();
        path.getNodes().forEach(n -> service.handle(new CompletePathNodeCommand(n.getId())));
        credentials.addValidated(60, 1, "HTTP essentials", "");

        assertThat(recognize(60).value()).isEmpty();
        assertThat(node(path, "http-basics").getLinkedCertificateId()).isNull();
    }

    @Test
    void recognize_aCertificateThatIsNotValidatedOrNotTheStudents_fails() {
        credentials.addUnverified(60, 1, "HTTP essentials", "");
        credentials.addValidated(61, 2, "HTTP essentials", "");

        assertFailure(recognize(60), LearningPathError.CERTIFICATE_NOT_VERIFIED);
        assertFailure(recognize(61), LearningPathError.NOT_CERTIFICATE_OWNER);
        assertFailure(recognize(99), LearningPathError.CERTIFICATE_NOT_FOUND);
    }

    @Test
    void recognize_whenSavingFails_returnsDatabaseError() {
        declare(REST_AND_JWT);
        credentials.addValidated(60, 1, "HTTP essentials", "");
        paths.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(recognize(60), LearningPathError.DATABASE_ERROR);
    }

    // ---------- US15: certificate-skill correspondence ----------

    @Test
    @DisplayName("US15 escenario 1: a certificate that covers the skill is linked to the node")
    void link_aCertificateThatCoversTheSkill_linksItToTheNode() {
        LearningPath path = declare(REST_AND_JWT).value();
        credentials.addUnverified(50, 1, "REST fundamentals", "");
        int restId = node(path, "rest-api-design").getId();
        int savesBefore = paths.saveCalls();

        Result<CertificateLinkOutcome> result = link(restId, 50);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().pathNodeId()).isEqualTo(restId);
        assertThat(result.value().certificateId()).isEqualTo(50);
        assertThat(result.value().affinity()).isEqualTo(1.0);
        PathNode rest = node(result.value().path(), "rest-api-design");
        assertThat(rest.getLinkedCertificateId()).isEqualTo(50);
        // Linking is evidence only: the node is demonstrated by its assessment.
        assertThat(rest.getStatus()).isEqualTo(NodeStatus.LOCKED);
        assertThat(paths.saveCalls()).isEqualTo(savesBefore + 1);
    }

    @Test
    void link_replacesTheCertificateLinkedBefore() {
        credentials.certificates().add(new CertificateSummary(10, "REST basics", null));
        LearningPath path = declare(REST_AND_JWT).value();
        credentials.addValidated(50, 1, "Advanced REST", "");

        link(node(path, "rest-api-design").getId(), 50);

        assertThat(node(path, "rest-api-design").getLinkedCertificateId()).isEqualTo(50);
        assertThat(node(path, "rest-api-design").isCompletedByCertificate()).isFalse();
    }

    @Test
    void link_withTheSkillOnlyInTheOcrText_isAboveTheThreshold() {
        LearningPath path = declare(REST_AND_JWT).value();
        credentials.addUnverified(50, 1, "Backend bootcamp", "Modules: REST services and JWT");

        Result<CertificateLinkOutcome> result = link(node(path, "rest-api-design").getId(), 50);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().affinity()).isEqualTo(0.75);
    }

    @Test
    @DisplayName("US15 escenario 2: a certificate that does not cover the skill is not linked and the right nodes are suggested")
    void link_aCertificateThatDoesNotCoverTheSkill_isNotLinkedAndSuggestsTheNodesItCovers() {
        LearningPath path = declare(REST_AND_JWT).value();
        credentials.addUnverified(51, 1, "HTTP essentials", "Also covers REST and JWT");
        int networkingId = node(path, "networking-basics").getId();
        int savesBefore = paths.saveCalls();

        Result<CertificateLinkOutcome> result = link(networkingId, 51);

        assertFailure(result, LearningPathError.CERTIFICATE_SKILL_MISMATCH);
        assertThat(result.details()).containsEntry("affinity", 0.0)
                .containsEntry("threshold", SkillAffinity.COVERAGE_THRESHOLD);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggested = (List<Map<String, Object>>) result.details().get("suggestedNodes");
        assertThat(suggested).extracting(s -> s.get("skillTag"))
                .containsExactly("http-basics", "rest-api-design", "authentication-jwt");
        assertThat(suggested.get(0)).containsEntry("nodeId", node(path, "http-basics").getId())
                .containsEntry("affinity", 1.0);
        assertThat(suggested.get(1)).containsEntry("affinity", 0.75);
        assertThat(node(path, "networking-basics").getLinkedCertificateId()).isNull();
        assertThat(paths.saveCalls()).isEqualTo(savesBefore);
    }

    @Test
    void link_aCertificateThatCoversNoNode_suggestsNothing() {
        LearningPath path = declare(REST_AND_JWT).value();
        credentials.addUnverified(51, 1, "Cooking basics", "");

        Result<CertificateLinkOutcome> result = link(node(path, "rest-api-design").getId(), 51);

        assertFailure(result, LearningPathError.CERTIFICATE_SKILL_MISMATCH);
        assertThat((List<?>) result.details().get("suggestedNodes")).isEmpty();
    }

    @Test
    void link_neverSuggestsACompletedNode() {
        LearningPath path = declare(REST_AND_JWT).value();
        service.handle(new CompletePathNodeCommand(node(path, "networking-basics").getId()));
        service.handle(new CompletePathNodeCommand(node(path, "http-basics").getId()));
        credentials.addUnverified(51, 1, "HTTP essentials", "");

        Result<CertificateLinkOutcome> result = link(node(path, "programming-fundamentals").getId(), 51);

        assertThat((List<?>) result.details().get("suggestedNodes")).isEmpty();
    }

    @Test
    void link_aCertificateThatIsNotEvidence_returnsCertificateNotVerified() {
        LearningPath path = declare(REST_AND_JWT).value();
        credentials.addNotEvidence(52, 1, "REST fundamentals");

        assertFailure(link(node(path, "rest-api-design").getId(), 52), LearningPathError.CERTIFICATE_NOT_VERIFIED);
    }

    @Test
    void link_anotherStudentsCertificate_returnsNotCertificateOwner() {
        LearningPath path = declare(REST_AND_JWT).value();
        credentials.addUnverified(53, 2, "REST fundamentals", "");

        assertFailure(link(node(path, "rest-api-design").getId(), 53), LearningPathError.NOT_CERTIFICATE_OWNER);
    }

    @Test
    void link_anUnknownCertificate_returnsCertificateNotFound() {
        LearningPath path = declare(REST_AND_JWT).value();

        assertFailure(link(node(path, "rest-api-design").getId(), 999), LearningPathError.CERTIFICATE_NOT_FOUND);
    }

    @Test
    void link_toAnotherStudentsNodeOrAnUnknownOne_fails() {
        LearningPath path = declare(REST_AND_JWT).value();
        credentials.addUnverified(50, 2, "REST fundamentals", "");

        assertFailure(link(node(path, "rest-api-design").getId(), 50, 2), LearningPathError.NOT_PATH_OWNER);
        assertFailure(link(999, 50), LearningPathError.NODE_NOT_FOUND);
    }

    @Test
    void link_toACompletedNode_returnsNodeAlreadyCompleted() {
        LearningPath path = declare(REST_AND_JWT).value();
        int networkingId = node(path, "networking-basics").getId();
        service.handle(new CompletePathNodeCommand(networkingId));
        credentials.addUnverified(50, 1, "Computer networking", "");

        assertFailure(link(networkingId, 50), LearningPathError.NODE_ALREADY_COMPLETED);
    }

    @Test
    void link_whenSavingFails_returnsDatabaseError() {
        LearningPath path = declare(REST_AND_JWT).value();
        credentials.addUnverified(50, 1, "REST fundamentals", "");
        paths.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(link(node(path, "rest-api-design").getId(), 50), LearningPathError.DATABASE_ERROR);
    }
}
