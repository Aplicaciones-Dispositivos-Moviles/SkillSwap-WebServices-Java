package com.innovify.skillswap.moderationdisputes.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the value as its text (see {@link DisputeStatus#value()}). */
@Converter(autoApply = true)
public class DisputeStatusConverter implements AttributeConverter<DisputeStatus, String> {

    @Override
    public String convertToDatabaseColumn(DisputeStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public DisputeStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : DisputeStatus.fromValue(dbData);
    }
}
