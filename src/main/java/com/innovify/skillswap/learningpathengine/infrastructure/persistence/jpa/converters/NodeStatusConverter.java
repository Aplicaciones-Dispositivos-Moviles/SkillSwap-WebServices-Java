package com.innovify.skillswap.learningpathengine.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the status as the text the C# API wrote ("Locked", "Available", "Completed"). */
@Converter(autoApply = true)
public class NodeStatusConverter implements AttributeConverter<NodeStatus, String> {

    @Override
    public String convertToDatabaseColumn(NodeStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public NodeStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : NodeStatus.fromValue(dbData);
    }
}
