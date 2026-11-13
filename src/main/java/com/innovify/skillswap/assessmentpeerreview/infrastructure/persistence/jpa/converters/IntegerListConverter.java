package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.shared.infrastructure.json.Json;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/**
 * Stores a list of integers in a jsonb column as a JSON array, as the C# API did: {@code [1,2,3,0,3]}. It is
 * not auto-applied (a list has no type of its own to match): the attribute names it explicitly. The JSON
 * travels as text; the datasource sends it untyped (stringtype=unspecified) so PostgreSQL reads it as jsonb.
 */
@Converter
public class IntegerListConverter implements AttributeConverter<List<Integer>, String> {

    @Override
    public String convertToDatabaseColumn(List<Integer> attribute) {
        return attribute == null ? null : Json.write(attribute);
    }

    @Override
    public List<Integer> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return ((List<?>) Json.parse(dbData)).stream().map(value -> ((Number) value).intValue()).toList();
    }
}
