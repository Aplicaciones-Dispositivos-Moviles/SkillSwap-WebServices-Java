package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the status as the text the C# API wrote ("Pending", "Assigned", "Resolved"). */
@Converter(autoApply = true)
public class CaseStatusConverter implements AttributeConverter<CaseStatus, String> {

    @Override
    public String convertToDatabaseColumn(CaseStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public CaseStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CaseStatus.fromValue(dbData);
    }
}
