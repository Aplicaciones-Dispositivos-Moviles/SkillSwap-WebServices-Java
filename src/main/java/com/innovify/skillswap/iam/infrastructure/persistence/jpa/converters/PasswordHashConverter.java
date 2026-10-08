package com.innovify.skillswap.iam.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.iam.domain.model.valueobjects.PasswordHash;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps the {@link PasswordHash} value object to its text column. */
@Converter(autoApply = true)
public class PasswordHashConverter implements AttributeConverter<PasswordHash, String> {

    @Override
    public String convertToDatabaseColumn(PasswordHash attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public PasswordHash convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new PasswordHash(dbData);
    }
}
