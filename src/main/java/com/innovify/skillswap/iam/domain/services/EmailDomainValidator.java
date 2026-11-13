package com.innovify.skillswap.iam.domain.services;

/** Contract for checking that an email belongs to an authorized institutional domain before an account is created. */
public interface EmailDomainValidator {

    boolean isInstitutionalDomain(String email);
}
