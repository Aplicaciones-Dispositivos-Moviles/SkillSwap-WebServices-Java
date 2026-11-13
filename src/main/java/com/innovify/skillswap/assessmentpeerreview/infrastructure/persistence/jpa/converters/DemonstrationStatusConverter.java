package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.DemonstrationStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the status as text ("Scheduled", "VideoSubmitted", "Approved"...). */
@Converter(autoApply = true)
public class DemonstrationStatusConverter implements AttributeConverter<DemonstrationStatus, String> {

    @Override
    public String convertToDatabaseColumn(DemonstrationStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public DemonstrationStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : DemonstrationStatus.fromValue(dbData);
    }
}
