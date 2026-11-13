package com.innovify.skillswap.recognitionincentives.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the credits as the plain integer column the C# API wrote. */
@Converter(autoApply = true)
public class CreditsConverter implements AttributeConverter<Credits, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Credits attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public Credits convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : new Credits(dbData);
    }
}
