package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the type as text, like the other states of the case ("Quiz", "MiniProject"). */
@Converter(autoApply = true)
public class CaseTypeConverter implements AttributeConverter<CaseType, String> {

    @Override
    public String convertToDatabaseColumn(CaseType attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public CaseType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CaseType.fromValue(dbData);
    }
}
