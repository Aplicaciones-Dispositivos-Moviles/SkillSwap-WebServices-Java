package com.innovify.skillswap.recognitionincentives.application.commandservices;

import com.innovify.skillswap.recognitionincentives.application.queryservices.ShareableCredentialView;

/** The credential of the student and whether this request generated its link (false when it already existed). */
public record IssuedCredential(ShareableCredentialView credential, boolean created) {
}
