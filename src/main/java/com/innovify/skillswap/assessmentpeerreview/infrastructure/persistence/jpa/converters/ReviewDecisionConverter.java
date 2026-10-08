package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the decision as the text the C# API wrote ("Approved", "Rejected"). */
@Converter(autoApply = true)
public class ReviewDecisionConverter implements AttributeConverter<ReviewDecision, String> {

    @Override
    public String convertToDatabaseColumn(ReviewDecision attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ReviewDecision convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ReviewDecision.fromValue(dbData);
    }
}
