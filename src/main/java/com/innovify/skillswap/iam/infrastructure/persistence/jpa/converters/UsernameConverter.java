package com.innovify.skillswap.iam.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.iam.domain.model.valueobjects.Username;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps the {@link Username} value object to its text column. */
@Converter(autoApply = true)
public class UsernameConverter implements AttributeConverter<Username, String> {

    @Override
    public String convertToDatabaseColumn(Username attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public Username convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new Username(dbData);
    }
}
