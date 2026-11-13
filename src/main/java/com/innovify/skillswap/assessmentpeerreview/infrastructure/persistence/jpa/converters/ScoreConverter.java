package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.Score;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the score as the text the C# API wrote: the correct answers over the total, e.g. "4/5". */
@Converter(autoApply = true)
public class ScoreConverter implements AttributeConverter<Score, String> {

    @Override
    public String convertToDatabaseColumn(Score attribute) {
        return attribute == null ? null : attribute.value() + "/" + attribute.total();
    }

    @Override
    public Score convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        String[] parts = dbData.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Unknown Score: " + dbData);
        }
        return new Score(Integer.parseInt(parts[0].strip()), Integer.parseInt(parts[1].strip()));
    }
}
