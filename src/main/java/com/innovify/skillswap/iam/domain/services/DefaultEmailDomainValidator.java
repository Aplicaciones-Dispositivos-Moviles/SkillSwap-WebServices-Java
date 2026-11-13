package com.innovify.skillswap.iam.domain.services;

import com.innovify.skillswap.iam.domain.model.valueobjects.Email;

/** Domain service implementation: pure business rule with no external dependencies. */
public class DefaultEmailDomainValidator implements EmailDomainValidator {

    @Override
    public boolean isInstitutionalDomain(String email) {
        return Email.isValid(email);
    }
}
