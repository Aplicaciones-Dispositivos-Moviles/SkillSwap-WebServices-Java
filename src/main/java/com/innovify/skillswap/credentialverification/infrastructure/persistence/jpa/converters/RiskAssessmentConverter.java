package com.innovify.skillswap.credentialverification.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskAssessment;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Only the score is stored (column risk_score); the risk level is derived from it by the value object. */
@Converter(autoApply = true)
public class RiskAssessmentConverter implements AttributeConverter<RiskAssessment, Integer> {

    @Override
    public Integer convertToDatabaseColumn(RiskAssessment attribute) {
        return attribute == null ? null : attribute.score();
    }

    @Override
    public RiskAssessment convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : new RiskAssessment(dbData);
    }
}
