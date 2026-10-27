package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.DemonstrationModality;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the modality as text ("AdvancedProject" or "SupervisedExam"). */
@Converter(autoApply = true)
public class DemonstrationModalityConverter implements AttributeConverter<DemonstrationModality, String> {

    @Override
    public String convertToDatabaseColumn(DemonstrationModality attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public DemonstrationModality convertToEntityAttribute(String dbData) {
        return dbData == null ? null : DemonstrationModality.fromValue(dbData);
    }
}
