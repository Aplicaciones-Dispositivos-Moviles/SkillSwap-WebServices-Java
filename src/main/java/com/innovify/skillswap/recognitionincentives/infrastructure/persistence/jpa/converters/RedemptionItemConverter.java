package com.innovify.skillswap.recognitionincentives.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.RedemptionItem;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the benefit as the name the API exposes ("AdvancedPathUnlock", "ContributionCertificate"). */
@Converter(autoApply = true)
public class RedemptionItemConverter implements AttributeConverter<RedemptionItem, String> {

    @Override
    public String convertToDatabaseColumn(RedemptionItem attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public RedemptionItem convertToEntityAttribute(String dbData) {
        return dbData == null ? null : RedemptionItem.fromValue(dbData);
    }
}
