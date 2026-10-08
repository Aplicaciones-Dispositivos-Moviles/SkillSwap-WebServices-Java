package com.innovify.skillswap.credentialverification.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationMethod;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the method as the text the C# API wrote ("OcrOnly", "Manual", ...). */
@Converter(autoApply = true)
public class VerificationMethodConverter implements AttributeConverter<VerificationMethod, String> {

    @Override
    public String convertToDatabaseColumn(VerificationMethod attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public VerificationMethod convertToEntityAttribute(String dbData) {
        return dbData == null ? null : VerificationMethod.fromValue(dbData);
    }
}
