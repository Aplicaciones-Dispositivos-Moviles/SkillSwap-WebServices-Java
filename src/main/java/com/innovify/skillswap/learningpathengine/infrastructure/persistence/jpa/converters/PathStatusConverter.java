package com.innovify.skillswap.learningpathengine.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the status as the text the C# API wrote ("Active", "Completed"). */
@Converter(autoApply = true)
public class PathStatusConverter implements AttributeConverter<PathStatus, String> {

    @Override
    public String convertToDatabaseColumn(PathStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public PathStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PathStatus.fromValue(dbData);
    }
}
