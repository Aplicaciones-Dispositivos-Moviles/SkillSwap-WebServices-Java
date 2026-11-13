package com.innovify.skillswap.moderationdisputes.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeOutcome;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the value as its text (see {@link DisputeOutcome#value()}). */
@Converter(autoApply = true)
public class DisputeOutcomeConverter implements AttributeConverter<DisputeOutcome, String> {

    @Override
    public String convertToDatabaseColumn(DisputeOutcome attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public DisputeOutcome convertToEntityAttribute(String dbData) {
        return dbData == null ? null : DisputeOutcome.fromValue(dbData);
    }
}
