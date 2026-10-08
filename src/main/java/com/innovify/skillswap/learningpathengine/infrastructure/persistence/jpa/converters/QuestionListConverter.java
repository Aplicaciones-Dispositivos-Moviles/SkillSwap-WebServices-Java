package com.innovify.skillswap.learningpathengine.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.learningpathengine.domain.model.entities.Question;
import com.innovify.skillswap.shared.infrastructure.json.Json;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores the questions of a blueprint in its jsonb column, as the C# API did:
 * {@code [{"question": "...", "answers": ["..."], "correctAnswer": 0}]}. It is not auto-applied (a list has no
 * type of its own to match): {@code AssessmentBlueprint.questions} names it explicitly.
 */
@Converter
public class QuestionListConverter implements AttributeConverter<List<Question>, String> {

    @Override
    public String convertToDatabaseColumn(List<Question> attribute) {
        if (attribute == null) {
            return null;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Question question : attribute) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("question", question.getQuestionString());
            item.put("answers", question.getAnswers());
            item.put("correctAnswer", question.getCorrectAnswer());
            items.add(item);
        }
        return Json.write(items);
    }

    @Override
    public List<Question> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        List<Question> questions = new ArrayList<>();
        for (Object entry : (List<?>) Json.parse(dbData)) {
            Map<?, ?> item = (Map<?, ?>) entry;
            List<String> answers = ((List<?>) item.get("answers")).stream().map(String::valueOf).toList();
            questions.add(new Question((String) item.get("question"), answers,
                    ((Number) item.get("correctAnswer")).intValue()));
        }
        return List.copyOf(questions);
    }
}
