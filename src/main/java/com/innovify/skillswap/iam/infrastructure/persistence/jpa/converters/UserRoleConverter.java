package com.innovify.skillswap.iam.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the role as the text the C# API wrote ("Student", "Coordinator"). */
@Converter(autoApply = true)
public class UserRoleConverter implements AttributeConverter<UserRole, String> {

    @Override
    public String convertToDatabaseColumn(UserRole attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public UserRole convertToEntityAttribute(String dbData) {
        return dbData == null ? null : UserRole.fromValue(dbData);
    }
}
