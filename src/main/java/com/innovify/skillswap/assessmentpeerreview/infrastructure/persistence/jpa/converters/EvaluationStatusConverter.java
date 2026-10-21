package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.EvaluationStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the status as text, like the states of the case ("Pending", "Approved", "Escalated"...). */
@Converter(autoApply = true)
public class EvaluationStatusConverter implements AttributeConverter<EvaluationStatus, String> {

    @Override
    public String convertToDatabaseColumn(EvaluationStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public EvaluationStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EvaluationStatus.fromValue(dbData);
    }
}
