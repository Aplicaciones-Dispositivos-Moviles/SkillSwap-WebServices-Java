package com.innovify.skillswap.reputation.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.reputation.domain.model.valueobjects.EmployabilityScore;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the score as the plain integer column the C# API wrote. */
@Converter(autoApply = true)
public class EmployabilityScoreConverter implements AttributeConverter<EmployabilityScore, Integer> {

    @Override
    public Integer convertToDatabaseColumn(EmployabilityScore attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public EmployabilityScore convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : new EmployabilityScore(dbData);
    }
}
