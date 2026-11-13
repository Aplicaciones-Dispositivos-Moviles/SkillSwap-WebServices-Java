package com.innovify.skillswap.moderationdisputes.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeSourceType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the value as its text (see {@link DisputeSourceType#value()}). */
@Converter(autoApply = true)
public class DisputeSourceTypeConverter implements AttributeConverter<DisputeSourceType, String> {

    @Override
    public String convertToDatabaseColumn(DisputeSourceType attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public DisputeSourceType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : DisputeSourceType.fromValue(dbData);
    }
}
