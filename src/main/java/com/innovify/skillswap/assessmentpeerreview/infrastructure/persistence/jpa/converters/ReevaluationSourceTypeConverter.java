package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the source type as text, like the other states of the context ("Attempt", "Case"). */
@Converter(autoApply = true)
public class ReevaluationSourceTypeConverter implements AttributeConverter<ReevaluationSourceType, String> {

    @Override
    public String convertToDatabaseColumn(ReevaluationSourceType attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ReevaluationSourceType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ReevaluationSourceType.fromValue(dbData);
    }
}
