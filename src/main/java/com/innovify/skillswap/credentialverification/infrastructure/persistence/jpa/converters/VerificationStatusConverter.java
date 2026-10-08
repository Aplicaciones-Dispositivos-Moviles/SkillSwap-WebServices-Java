package com.innovify.skillswap.credentialverification.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the status as the text the C# API wrote ("Pending", "Unverified", "Suspicious", ...). */
@Converter(autoApply = true)
public class VerificationStatusConverter implements AttributeConverter<VerificationStatus, String> {

    @Override
    public String convertToDatabaseColumn(VerificationStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public VerificationStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : VerificationStatus.fromValue(dbData);
    }
}
