package com.innovify.skillswap.learningpathengine.application.internal.commandservices;

import com.innovify.skillswap.credentialverification.application.acl.CertificateEvidence;
import com.innovify.skillswap.credentialverification.application.acl.CertificateSummary;
import com.innovify.skillswap.credentialverification.application.acl.CredentialContextFacade;
import com.innovify.skillswap.learningpathengine.application.commandservices.CertificateLinkOutcome;
import com.innovify.skillswap.learningpathengine.application.commandservices.LearningPathCommandService;
import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.CertificateContent;
import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.CertificateSkillAffinityScorer;
import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.SkillTaxonomyMatcher;
import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.commands.CompletePathNodeCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.DeclareGoalCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.EnforcePlanLimitsCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.LinkCertificateToNodeCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.PauseLearningPathCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.RecognizeValidatedCertificateCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.RefreshCertificateLinksCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.ResumeLearningPathCommand;
import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.CareerGoal;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.SkillAffinity;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.SkillGap;
import com.innovify.skillswap.learningpathengine.domain.repositories.LearningPathRepository;
import com.innovify.skillswap.learningpathengine.domain.services.LearningPathBuilder;
import com.innovify.skillswap.learningpathengine.domain.services.SkillGapAnalyzer;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.subscriptionbilling.application.acl.PlanLimitsView;
import com.innovify.skillswap.subscriptionbilling.application.acl.SubscriptionContextFacade;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Learning path command service.
 *
 * <p>It is deliberately not {@code @Transactional}: {@link LearningPathRepository#save} commits on its own, so
 * a persistence failure is caught here and returned as a {@link Result}. The changes that depend on the limits of
 * the plan of the student (a new path, a resumed path, the paths paused after a downgrade) run in the given
 * {@link TransactionOperations} after {@link LearningPathRepository#lockStudentPaths}: two requests of the same
 * student wait for each other, so they cannot both see room under the limit.
 *
 * <p>Certificates are read from Credential Verification through its facade, and compared with the skills by the
 * {@link CertificateSkillAffinityScorer}. A certificate validated by a verifier that covers a skill makes it count
 * as demonstrated (its node is completed); any other certificate that can be evidence is only linked to the node.
 */
@Service
public class LearningPathCommandServiceImpl implements LearningPathCommandService {

    private static final Logger log = LoggerFactory.getLogger(LearningPathCommandServiceImpl.class);

    private final LearningPathRepository learningPathRepository;
    private final SkillTaxonomyMatcher taxonomyMatcher;
    private final CertificateSkillAffinityScorer affinityScorer;
    private final SkillGapAnalyzer skillGapAnalyzer;
    private final LearningPathBuilder learningPathBuilder;
    private final CredentialContextFacade credentialContextFacade;
    private final SubscriptionContextFacade subscriptionContextFacade;
    private final TransactionOperations transactions;
    private final LearningPathFailures failures;

    public LearningPathCommandServiceImpl(LearningPathRepository learningPathRepository,
                                          SkillTaxonomyMatcher taxonomyMatcher,
                                          CertificateSkillAffinityScorer affinityScorer,
                                          SkillGapAnalyzer skillGapAnalyzer,
                                          LearningPathBuilder learningPathBuilder,
                                          CredentialContextFacade credentialContextFacade,
                                          SubscriptionContextFacade subscriptionContextFacade,
                                          TransactionOperations transactions,
                                          MessageSource messageSource) {
        this.learningPathRepository = learningPathRepository;
        this.taxonomyMatcher = taxonomyMatcher;
        this.affinityScorer = affinityScorer;
        this.skillGapAnalyzer = skillGapAnalyzer;
        this.learningPathBuilder = learningPathBuilder;
        this.credentialContextFacade = credentialContextFacade;
        this.subscriptionContextFacade = subscriptionContextFacade;
        this.transactions = transactions;
        this.failures = new LearningPathFailures(messageSource);
    }

    @Override
    public Result<LearningPath> handle(DeclareGoalCommand command) {
        String text = command.rawText() == null ? "" : command.rawText().strip();
        if (text.isEmpty() || text.length() > CareerGoal.MAX_RAW_TEXT_LENGTH) {
            return failures.failure(LearningPathError.INVALID_GOAL);
        }

        try {
            // Checked first so the app can offer the paid plan right away; checked again under the lock below.
            Optional<PlanLimitBreach> early = newPathBreach(command.studentId());
            if (early.isPresent()) {
                return failures.failure(LearningPathError.PLAN_LIMIT_REACHED, early.get().toDetails());
            }

            List<String> skillTags = taxonomyMatcher.match(text);
            if (skillTags.isEmpty()) {
                return failures.failure(LearningPathError.GOAL_NOT_INTERPRETABLE);
            }

            CareerGoal goal = new CareerGoal(text, skillTags);
            Collection<String> demonstrated =
                    learningPathRepository.findCompletedSkillTagsByStudentId(command.studentId());
            SkillGap gap = skillGapAnalyzer.analyze(goal, demonstrated);
            if (gap.isEmpty()) {
                return failures.failure(LearningPathError.GOAL_ALREADY_ACHIEVED);
            }

            // A skill covered by a validated certificate counts as demonstrated: it stops the walk through its
            // prerequisites like any other, but keeps its node, completed and linked to the certificate.
            Map<String, Integer> certified = certifiedSkills(command.studentId(), gap.missingSkillTags());
            if (!certified.isEmpty()) {
                Set<String> known = new HashSet<>(demonstrated);
                known.addAll(certified.keySet());
                SkillGap certifiedGap = skillGapAnalyzer.analyze(goal, known);
                if (certifiedGap.isEmpty()) {
                    return failures.failure(LearningPathError.GOAL_ALREADY_ACHIEVED);
                }
                List<String> recognized = certifiedGap.verifiedSkillTags().stream()
                        .filter(certified::containsKey)
                        .toList();
                List<String> nodeSkills = new ArrayList<>(certifiedGap.missingSkillTags());
                nodeSkills.addAll(recognized);
                gap = new SkillGap(certifiedGap.verifiedSkillTags().stream()
                        .filter(tag -> !certified.containsKey(tag))
                        .toList(), nodeSkills);
            }

            LearningPath path = new LearningPath(command.studentId(), goal, learningPathBuilder.buildPath(gap));
            certified.forEach((skillTag, certificateId) -> path.recognizeCertifiedSkill(skillTag, certificateId));
            linkEvidence(path, command.studentId());

            return transactions.execute(status -> {
                learningPathRepository.lockStudentPaths(command.studentId());
                Optional<PlanLimitBreach> breach = newPathBreach(command.studentId());
                if (breach.isPresent()) {
                    return failures.<LearningPath>failure(LearningPathError.PLAN_LIMIT_REACHED,
                            breach.get().toDetails());
                }
                return Result.success(learningPathRepository.save(path));
            });
        } catch (RuntimeException exception) {
            return failureFrom(exception, "declare the goal of student " + command.studentId());
        }
    }

    @Override
    public Result<CertificateLinkOutcome> handle(LinkCertificateToNodeCommand command) {
        try {
            Optional<LearningPath> found = learningPathRepository.findByNodeId(command.pathNodeId());
            if (found.isEmpty()) {
                return failures.failure(LearningPathError.NODE_NOT_FOUND);
            }
            LearningPath path = found.get();
            if (path.getStudentId() != command.studentId()) {
                return failures.failure(LearningPathError.NOT_PATH_OWNER);
            }
            PathNode node = path.getNode(command.pathNodeId()).orElseThrow();
            if (node.getStatus() == NodeStatus.COMPLETED) {
                return failures.failure(LearningPathError.NODE_ALREADY_COMPLETED);
            }

            Optional<CertificateEvidence> certificate = credentialContextFacade.getCertificate(command.certificateId());
            if (certificate.isEmpty()) {
                return failures.failure(LearningPathError.CERTIFICATE_NOT_FOUND);
            }
            if (certificate.get().ownerId() != command.studentId()) {
                return failures.failure(LearningPathError.NOT_CERTIFICATE_OWNER);
            }
            if (!certificate.get().supportingEvidence()) {
                return failures.failure(LearningPathError.CERTIFICATE_NOT_VERIFIED);
            }

            // The certificate is compared with every node still to be demonstrated, to suggest the right ones.
            List<PathNode> openNodes = path.getNodes().stream()
                    .filter(n -> n.getStatus() != NodeStatus.COMPLETED)
                    .toList();
            Map<String, SkillAffinity> affinities = new LinkedHashMap<>();
            affinityScorer.score(contentOf(certificate.get()), openNodes.stream().map(PathNode::getSkillTag).toList())
                    .forEach(affinity -> affinities.put(affinity.skillTag(), affinity));

            SkillAffinity target = affinities.getOrDefault(node.getSkillTag(),
                    new SkillAffinity(node.getSkillTag(), 0));
            if (!target.covers()) {
                List<Map<String, Object>> suggestions = openNodes.stream()
                        .filter(n -> n.getId() != null && !n.getId().equals(node.getId()))
                        .filter(n -> affinities.containsKey(n.getSkillTag()) && affinities.get(n.getSkillTag()).covers())
                        .sorted(Comparator.comparingDouble((PathNode n) -> -affinities.get(n.getSkillTag()).score())
                                .thenComparingInt(PathNode::getOrder))
                        .map(n -> Map.<String, Object>of(
                                "nodeId", n.getId(),
                                "skillTag", n.getSkillTag(),
                                "affinity", affinities.get(n.getSkillTag()).score()))
                        .toList();
                return failures.failure(LearningPathError.CERTIFICATE_SKILL_MISMATCH, Map.of(
                        "affinity", target.score(),
                        "threshold", SkillAffinity.COVERAGE_THRESHOLD,
                        "suggestedNodes", suggestions));
            }

            path.associateCertificate(node.getId(), command.certificateId());
            LearningPath saved = learningPathRepository.save(path);
            return Result.success(new CertificateLinkOutcome(saved, node.getId(), command.certificateId(),
                    target.score()));
        } catch (RuntimeException exception) {
            log.error("Could not link the certificate {} to the node {}", command.certificateId(),
                    command.pathNodeId(), exception);
            return failures.failure(LearningPathFailures.toError(exception));
        }
    }

    @Override
    public Result<List<LearningPath>> handle(RecognizeValidatedCertificateCommand command) {
        try {
            Optional<CertificateEvidence> found = credentialContextFacade.getCertificate(command.certificateId());
            if (found.isEmpty()) {
                return failures.failure(LearningPathError.CERTIFICATE_NOT_FOUND);
            }
            CertificateEvidence certificate = found.get();
            if (certificate.ownerId() != command.studentId()) {
                return failures.failure(LearningPathError.NOT_CERTIFICATE_OWNER);
            }
            if (!certificate.validated()) {
                return failures.failure(LearningPathError.CERTIFICATE_NOT_VERIFIED);
            }

            List<LearningPath> changed = new ArrayList<>();
            for (LearningPath path : learningPathRepository.findByStudentId(command.studentId())) {
                if (path.getStatus() == PathStatus.COMPLETED) {
                    continue;
                }
                List<String> pendingSkills = path.getNodes().stream()
                        .filter(node -> node.getStatus() != NodeStatus.COMPLETED)
                        .map(PathNode::getSkillTag)
                        .toList();
                boolean recognizedAny = false;
                for (SkillAffinity affinity : affinityScorer.score(contentOf(certificate), pendingSkills)) {
                    if (affinity.covers()) {
                        recognizedAny |= path.recognizeCertifiedSkill(affinity.skillTag(), certificate.id());
                    }
                }
                if (recognizedAny) {
                    changed.add(learningPathRepository.save(path));
                }
            }
            return Result.success(List.copyOf(changed));
        } catch (RuntimeException exception) {
            log.error("Could not recognize the certificate {} of student {}", command.certificateId(),
                    command.studentId(), exception);
            return failures.failure(LearningPathFailures.toError(exception));
        }
    }

    @Override
    public Result<LearningPath> handle(CompletePathNodeCommand command) {
        try {
            Optional<LearningPath> found = learningPathRepository.findByNodeId(command.pathNodeId());
            if (found.isEmpty()) {
                return failures.failure(LearningPathError.NODE_NOT_FOUND);
            }
            LearningPath path = found.get();

            PathNode node = path.getNode(command.pathNodeId()).orElseThrow();
            if (node.getStatus() == NodeStatus.COMPLETED) {
                return failures.failure(LearningPathError.NODE_ALREADY_COMPLETED);
            }
            if (node.getStatus() == NodeStatus.LOCKED) {
                return failures.failure(LearningPathError.NODE_LOCKED,
                        Map.of("pendingPrerequisites", path.pendingPrerequisitesOf(node.getId())));
            }

            path.completeNode(node.getId());
            return Result.success(learningPathRepository.save(path));
        } catch (RuntimeException exception) {
            return failureFrom(exception, "complete the node " + command.pathNodeId());
        }
    }

    @Override
    public Result<LearningPath> handle(PauseLearningPathCommand command) {
        try {
            Optional<LearningPath> found = learningPathRepository.findById(command.pathId());
            if (found.isEmpty()) {
                return failures.failure(LearningPathError.PATH_NOT_FOUND);
            }
            LearningPath path = found.get();
            if (path.getStudentId() != command.studentId()) {
                return failures.failure(LearningPathError.NOT_PATH_OWNER);
            }
            if (!path.isActive()) {
                return failures.failure(LearningPathError.PATH_NOT_ACTIVE);
            }

            return Result.success(learningPathRepository.save(path.pause()));
        } catch (RuntimeException exception) {
            return failureFrom(exception, "pause the path " + command.pathId());
        }
    }

    @Override
    public Result<LearningPath> handle(ResumeLearningPathCommand command) {
        try {
            Optional<LearningPath> found = learningPathRepository.findById(command.pathId());
            if (found.isEmpty()) {
                return failures.failure(LearningPathError.PATH_NOT_FOUND);
            }
            if (found.get().getStudentId() != command.studentId()) {
                return failures.failure(LearningPathError.NOT_PATH_OWNER);
            }

            return transactions.execute(status -> {
                learningPathRepository.lockStudentPaths(command.studentId());
                // Read again under the lock: another request may have changed it meanwhile.
                LearningPath path = learningPathRepository.findById(command.pathId()).orElseThrow();
                if (!path.isPaused()) {
                    return failures.<LearningPath>failure(LearningPathError.PATH_NOT_PAUSED);
                }

                PlanLimitsView limits = subscriptionContextFacade.getPlanLimits(command.studentId());
                int active = learningPathRepository.countActiveByStudentId(command.studentId());
                if (active >= limits.maxActiveRoutes()) {
                    return failures.<LearningPath>failure(LearningPathError.PLAN_LIMIT_REACHED,
                            PlanLimitBreach.activeRoutes(limits, active).toDetails());
                }
                return Result.success(learningPathRepository.save(path.resume()));
            });
        } catch (RuntimeException exception) {
            return failureFrom(exception, "resume the path " + command.pathId());
        }
    }

    @Override
    public Result<List<LearningPath>> handle(EnforcePlanLimitsCommand command) {
        try {
            return transactions.execute(status -> {
                learningPathRepository.lockStudentPaths(command.studentId());
                PlanLimitsView limits = subscriptionContextFacade.getPlanLimits(command.studentId());

                // The path the student advanced on most recently stays active; ties go to the newest path.
                List<LearningPath> active = learningPathRepository.findByStudentId(command.studentId()).stream()
                        .filter(LearningPath::isActive)
                        .sorted(Comparator.comparing(LearningPath::getLastProgressAt)
                                .thenComparing(LearningPath::getId).reversed())
                        .toList();

                List<LearningPath> paused = new ArrayList<>();
                for (LearningPath path : active.subList(Math.min(limits.maxActiveRoutes(), active.size()),
                        active.size())) {
                    paused.add(learningPathRepository.save(path.pause()));
                }
                return Result.success(List.copyOf(paused));
            });
        } catch (RuntimeException exception) {
            log.error("Could not apply the plan limits to the paths of student {}", command.studentId(), exception);
            return failures.failure(LearningPathFailures.toError(exception));
        }
    }

    @Override
    public Result<LearningPath> handle(RefreshCertificateLinksCommand command) {
        try {
            Optional<LearningPath> found = learningPathRepository.findLatestByStudentId(command.studentId());
            if (found.isEmpty()) {
                return failures.failure(LearningPathError.PATH_NOT_FOUND);
            }
            LearningPath path = found.get();

            // Only an active path can still change. The links are supporting evidence, so a failure to save
            // them must not fail the read: the next read tries again.
            if (path.getStatus() == PathStatus.ACTIVE && linkEvidence(path, command.studentId())) {
                try {
                    path = learningPathRepository.save(path);
                } catch (RuntimeException exception) {
                    log.warn("The certificate links of student {} could not be saved", command.studentId(),
                            exception);
                }
            }

            return Result.success(path);
        } catch (RuntimeException exception) {
            return failureFrom(exception, "refresh the certificate links of student " + command.studentId());
        }
    }

    /**
     * Links the student's certificates to the nodes whose skill their course name covers. Such a link is only
     * supporting evidence: it never completes a node. It is informational, so a failure here must not prevent
     * the path from being created or read. Only the course name is compared, so it is cheap enough for every read.
     *
     * @return whether at least one new link was made
     */
    private boolean linkEvidence(LearningPath path, int studentId) {
        boolean linkedAny = false;
        try {
            List<String> openSkills = path.getNodes().stream()
                    .filter(node -> node.getStatus() != NodeStatus.COMPLETED && node.getLinkedCertificateId() == null)
                    .map(PathNode::getSkillTag)
                    .toList();
            if (openSkills.isEmpty()) {
                return false;
            }
            for (CertificateSummary certificate : credentialContextFacade.getEvidenceCertificates(studentId)) {
                if (certificate.courseName() == null || certificate.courseName().isBlank()) {
                    continue;
                }
                for (SkillAffinity affinity : affinityScorer.score(
                        new CertificateContent(certificate.courseName(), null), openSkills)) {
                    if (affinity.covers()) {
                        linkedAny |= path.linkCertificateToSkill(affinity.skillTag(), certificate.id());
                    }
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Certificates could not be linked to the path of student {}", studentId, exception);
        }
        return linkedAny;
    }

    /**
     * The skills, among the given ones, covered by a certificate of the student that a verifier validated, each
     * with the oldest such certificate. Best effort: when the certificates cannot be read, none is recognized and
     * the path is generated as if the student had none.
     */
    private Map<String, Integer> certifiedSkills(int studentId, List<String> skillTags) {
        Map<String, Integer> certified = new LinkedHashMap<>();
        try {
            for (CertificateEvidence certificate : credentialContextFacade.getValidatedCertificates(studentId)) {
                for (SkillAffinity affinity : affinityScorer.score(contentOf(certificate), skillTags)) {
                    if (affinity.covers()) {
                        certified.putIfAbsent(affinity.skillTag(), certificate.id());
                    }
                }
            }
        } catch (RuntimeException exception) {
            log.warn("The validated certificates of student {} could not be read", studentId, exception);
            return Map.of();
        }
        return certified;
    }

    private static CertificateContent contentOf(CertificateEvidence certificate) {
        return new CertificateContent(certificate.courseName(), certificate.ocrText());
    }

    /** Why the plan does not allow the student another active path, if it does not. */
    private Optional<PlanLimitBreach> newPathBreach(int studentId) {
        PlanLimitsView limits = subscriptionContextFacade.getPlanLimits(studentId);
        // The total is checked first: pausing the active path does not help with it.
        if (limits.maxTotalRoutes() != null) {
            int total = learningPathRepository.countByStudentId(studentId);
            if (total >= limits.maxTotalRoutes()) {
                return Optional.of(PlanLimitBreach.totalRoutes(limits, total));
            }
        }
        int active = learningPathRepository.countActiveByStudentId(studentId);
        if (active >= limits.maxActiveRoutes()) {
            return Optional.of(PlanLimitBreach.activeRoutes(limits, active));
        }
        return Optional.empty();
    }

    private Result<LearningPath> failureFrom(RuntimeException exception, String operation) {
        log.error("Could not {}", operation, exception);
        return failures.failure(LearningPathFailures.toError(exception));
    }
}
