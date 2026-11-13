package com.innovify.skillswap.learningpathengine.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.CareerGoal;
import com.innovify.skillswap.shared.infrastructure.json.Json;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores the goal in the jsonb column of the C# API: {@code {"rawText": "...", "skillTags": ["..."]}}. The
 * JSON travels as text; the datasource sends it untyped (stringtype=unspecified) so PostgreSQL reads it as
 * jsonb.
 */
@Converter(autoApply = true)
public class CareerGoalConverter implements AttributeConverter<CareerGoal, String> {

    @Override
    public String convertToDatabaseColumn(CareerGoal attribute) {
        if (attribute == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("rawText", attribute.rawText());
        json.put("skillTags", attribute.mappedSkillTags());
        return Json.write(json);
    }

    @Override
    public CareerGoal convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        Map<?, ?> json = (Map<?, ?>) Json.parse(dbData);
        List<?> tags = (List<?>) json.get("skillTags");
        return new CareerGoal((String) json.get("rawText"), tags.stream().map(String::valueOf).toList());
    }
}
