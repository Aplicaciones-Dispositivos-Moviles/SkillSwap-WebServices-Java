package com.innovify.skillswap.iam.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.shared.infrastructure.json.Json;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/**
 * Stores a list of strings in a jsonb column as a JSON array, e.g. {@code ["Desarrollo web","Java"]}. It is not
 * auto-applied: the attribute names it explicitly.
 */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        return Json.write(attribute == null ? List.of() : attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        return ((List<?>) Json.parse(dbData)).stream().map(String::valueOf).toList();
    }
}
