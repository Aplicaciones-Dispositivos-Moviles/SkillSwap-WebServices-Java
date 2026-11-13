package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.shared.infrastructure.json.Json;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/**
 * Stores a list of strings in a jsonb column as a JSON array, as the C# API did:
 * {@code ["http-basics","rest-api-design"]}. It is not auto-applied: the attribute names it explicitly.
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        return attribute == null ? null : Json.write(attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return ((List<?>) Json.parse(dbData)).stream().map(String::valueOf).toList();
    }
}
