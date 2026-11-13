package com.innovify.skillswap.learningpathengine.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.learningpathengine.domain.model.entities.RubricCriterion;
import com.innovify.skillswap.shared.infrastructure.json.Json;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores the rubric of a mini-project in its jsonb column:
 * {@code [{"name": "...", "description": "...", "weight": 30}]}. It is not auto-applied (a list has no type of its
 * own to match): {@code MiniProject.rubric} names it explicitly.
 */
@Converter
public class RubricCriterionListConverter implements AttributeConverter<List<RubricCriterion>, String> {

    @Override
    public String convertToDatabaseColumn(List<RubricCriterion> attribute) {
        if (attribute == null) {
            return null;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (RubricCriterion criterion : attribute) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", criterion.getName());
            item.put("description", criterion.getDescription());
            item.put("weight", criterion.getWeight());
            items.add(item);
        }
        return Json.write(items);
    }

    @Override
    public List<RubricCriterion> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        List<RubricCriterion> rubric = new ArrayList<>();
        for (Object entry : (List<?>) Json.parse(dbData)) {
            Map<?, ?> item = (Map<?, ?>) entry;
            rubric.add(new RubricCriterion((String) item.get("name"), (String) item.get("description"),
                    ((Number) item.get("weight")).intValue()));
        }
        return List.copyOf(rubric);
    }
}
