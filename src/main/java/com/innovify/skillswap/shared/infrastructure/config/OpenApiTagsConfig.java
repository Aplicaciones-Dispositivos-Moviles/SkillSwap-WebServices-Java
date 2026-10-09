package com.innovify.skillswap.shared.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gives the Swagger UI groups a readable name and a short description, instead of the default
 * "xxx-controller" name that springdoc derives from each class. The groups keep this order in the UI.
 */
@Configuration
public class OpenApiTagsConfig {

    /** Default springdoc tag (kebab-case of the controller class) -> readable group. */
    private static final Map<String, Tag> GROUPS = new LinkedHashMap<>();

    static {
        group("authentication-controller", "Authentication",
                "Identity & Access. Sign up with an institutional (.edu.pe) email and sign in to get the JWT.");
        group("users-controller", "Users",
                "Identity & Access. The authenticated user and the profile bio.");
        group("learning-paths-controller", "Learning Paths",
                "Learning Path Engine. Declare a goal and get the certification path with its nodes.");
        group("advanced-path-unlocks-controller", "Advanced Path Unlocks",
                "Learning Path Engine. Advanced paths redeemed with SkillCredits, which do not count toward the "
                        + "limits of the plan.");
        group("assessment-blueprints-controller", "Assessment Blueprints",
                "Learning Path Engine. Generate the AI questions that assess a path node.");
        group("certificates-controller", "Certificates",
                "Credential Verification. Upload a certificate (JPG, PNG or PDF) and check its risk and status.");
        group("assessment-attempts-controller", "Assessment Attempts",
                "Assessment & Peer Review. Submit the answers of a blueprint; a failed attempt opens a case.");
        group("verification-cases-controller", "Verification Cases",
                "Assessment & Peer Review. Attach evidence, resolve a case as verifier and appeal a rejection.");
        group("verifier-profiles-controller", "Verifier Profiles",
                "Assessment & Peer Review. Become a verifier for a skill and set the availability.");
        group("disputes-controller", "Disputes",
                "Moderation & Disputes. Suspicious certificates escalated to a Verificador senior: list the ones "
                        + "assigned to you, read their evidence and resolve them.");
        group("verifier-reliabilities-controller", "Verifier Reliabilities",
                "Reputation. Reliability score of a verifier (owner only).");
        group("student-employability-scores-controller", "Student Employability Scores",
                "Reputation. Employability score of a student, from the certified skills (owner only).");
        group("wallets-controller", "Wallets",
                "Recognition & Incentives. SkillCredits balance and transaction history (owner only).");
        group("credit-transactions-controller", "Credit Transactions",
                "Recognition & Incentives. Redeem SkillCredits for a benefit.");
        group("subscriptions-controller", "Subscriptions",
                "Subscription & Billing. Activate the monthly plan after a Google Play purchase (verified with "
                        + "RevenueCat), read the plan limits and cancel.");
        group("revenue-cat-webhook-controller", "RevenueCat Webhook",
                "Subscription & Billing. Notifications of RevenueCat, protected by its own Authorization secret.");
        group("health-controller", "Health", "Service health check, used by the host and the keep-alive ping.");
    }

    private static void group(String springdocName, String name, String description) {
        GROUPS.put(springdocName, new Tag().name(name).description(description));
    }

    @Bean
    public OpenApiCustomizer readableTagsCustomizer() {
        return openApi -> {
            renameOperationTags(openApi);
            openApi.setTags(new ArrayList<>(GROUPS.values()));
        };
    }

    private static void renameOperationTags(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }
        for (PathItem pathItem : openApi.getPaths().values()) {
            pathItem.readOperations().forEach(operation -> {
                if (operation.getTags() == null) {
                    return;
                }
                List<String> renamed = new ArrayList<>();
                for (String tag : operation.getTags()) {
                    Tag group = GROUPS.get(tag);
                    renamed.add(group == null ? tag : group.getName());
                }
                operation.setTags(renamed);
            });
        }
    }
}
