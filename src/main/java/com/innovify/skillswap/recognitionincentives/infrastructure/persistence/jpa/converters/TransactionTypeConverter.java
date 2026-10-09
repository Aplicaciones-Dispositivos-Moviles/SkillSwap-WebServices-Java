package com.innovify.skillswap.recognitionincentives.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.TransactionType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the type as the text the C# API wrote ("Earned", "Redeemed"). */
@Converter(autoApply = true)
public class TransactionTypeConverter implements AttributeConverter<TransactionType, String> {

    @Override
    public String convertToDatabaseColumn(TransactionType attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public TransactionType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TransactionType.fromValue(dbData);
    }
}
