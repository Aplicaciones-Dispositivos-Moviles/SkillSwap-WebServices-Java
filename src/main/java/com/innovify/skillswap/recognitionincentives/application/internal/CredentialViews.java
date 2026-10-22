package com.innovify.skillswap.recognitionincentives.application.internal;

import com.innovify.skillswap.recognitionincentives.application.queryservices.PublicCredentialView;
import com.innovify.skillswap.recognitionincentives.application.queryservices.ShareableCredentialView;
import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.ShareableCredential;
import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.repositories.WalletRepository;
import com.innovify.skillswap.iam.application.acl.IamContextFacade;
import com.innovify.skillswap.learningpathengine.application.acl.SkillCatalogContextFacade;
import java.util.List;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * Builds the views of the credentials. It names the skills and the students through the facades of the other
 * contexts and reads the SkillCredits from the wallets of this one.
 */
public class CredentialViews {

    /** Path of the public page of a credential; the token goes at the end. */
    public static final String PUBLIC_PATH = "/api/v1/public/credentials/";
    private static final String SHARE_TEXT_KEY = "ShareCredentialText";

    private final WalletRepository wallets;
    private final SkillCatalogContextFacade skillCatalog;
    private final IamContextFacade iam;
    private final MessageSource messageSource;
    private final String baseUrl;

    public CredentialViews(WalletRepository wallets, SkillCatalogContextFacade skillCatalog, IamContextFacade iam,
                           MessageSource messageSource, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("The base URL of the credentials is required.");
        }
        String stripped = baseUrl.strip();
        this.wallets = wallets;
        this.skillCatalog = skillCatalog;
        this.iam = iam;
        this.messageSource = messageSource;
        this.baseUrl = stripped.endsWith("/") ? stripped.substring(0, stripped.length() - 1) : stripped;
    }

    public String urlOf(ShareableCredential credential) {
        return baseUrl + PUBLIC_PATH + credential.getToken();
    }

    /** The credentials of one owner, in the same order. */
    public List<ShareableCredentialView> toViews(int ownerId, List<ShareableCredential> credentials) {
        if (credentials.isEmpty()) {
            return List.of();
        }
        int credits = creditsOf(ownerId);
        Map<String, String> names = skillCatalog.getSkillNames(
                credentials.stream().map(ShareableCredential::getSkillTag).distinct().toList());
        return credentials.stream().map(credential -> toView(credential, credits, names)).toList();
    }

    public ShareableCredentialView toView(ShareableCredential credential) {
        return toViews(credential.getOwnerId(), List.of(credential)).get(0);
    }

    public PublicCredentialView toPublicView(ShareableCredential credential) {
        String tag = credential.getSkillTag();
        return new PublicCredentialView(iam.getRegisteredFullName(credential.getOwnerId()).orElse(null), tag,
                skillCatalog.getSkillNames(List.of(tag)).getOrDefault(tag, tag), creditsOf(credential.getOwnerId()),
                credential.getCreatedAt());
    }

    private ShareableCredentialView toView(ShareableCredential credential, int credits, Map<String, String> names) {
        String tag = credential.getSkillTag();
        String name = names.getOrDefault(tag, tag);
        String url = urlOf(credential);
        String text = messageSource.getMessage(SHARE_TEXT_KEY, new Object[] {name, credits, url},
                LocaleContextHolder.getLocale());
        return new ShareableCredentialView(tag, name, credential.getToken(), url, text, credential.getCreatedAt());
    }

    private int creditsOf(int ownerId) {
        return wallets.findByOwnerId(ownerId).map(Wallet::getBalance).orElse(0);
    }
}
