package com.innovify.skillswap.reputation.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.reputation.domain.model.valueobjects.ReliabilityScore;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the score as the plain integer column the C# API wrote. */
@Converter(autoApply = true)
public class ReliabilityScoreConverter implements AttributeConverter<ReliabilityScore, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ReliabilityScore attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ReliabilityScore convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : new ReliabilityScore(dbData);
    }
}
