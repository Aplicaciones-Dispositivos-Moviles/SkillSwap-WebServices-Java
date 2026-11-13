package com.innovify.skillswap.recognitionincentives.interfaces.rest;

import com.innovify.skillswap.recognitionincentives.application.queryservices.ShareableCredentialQueryService;
import com.innovify.skillswap.recognitionincentives.domain.model.RecognitionIncentivesError;
import com.innovify.skillswap.recognitionincentives.domain.model.queries.GetPublicCredentialByTokenQuery;
import com.innovify.skillswap.recognitionincentives.interfaces.rest.transform.RecognitionIncentivesResourceAssemblers;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public page of a credential. It needs no account: the unguessable token in the link is the only key, and it
 * shows the name, the skill and the SkillCredits, never the email or the id of the student.
 */
@RestController
@RequestMapping("/api/v1/public/credentials")
public class PublicCredentialsController {

    private final ShareableCredentialQueryService queryService;
    private final MessageSource messageSource;

    public PublicCredentialsController(ShareableCredentialQueryService queryService, MessageSource messageSource) {
        this.queryService = queryService;
        this.messageSource = messageSource;
    }

    /** The credential. 200, or 404 when the token does not exist. */
    @GetMapping("/{token}")
    public ResponseEntity<?> getByToken(@PathVariable String token) {
        var credential = queryService.handle(new GetPublicCredentialByTokenQuery(token));
        if (credential.isEmpty()) {
            return RecognitionIncentivesErrorResponses.of(messageSource,
                    RecognitionIncentivesError.CREDENTIAL_NOT_FOUND);
        }
        return ResponseEntity.ok(RecognitionIncentivesResourceAssemblers.toResource(credential.get()));
    }
}
