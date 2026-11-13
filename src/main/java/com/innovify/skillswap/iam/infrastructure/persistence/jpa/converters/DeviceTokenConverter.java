package com.innovify.skillswap.iam.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.iam.domain.model.valueobjects.DeviceToken;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps the {@link DeviceToken} value object to its text column. */
@Converter(autoApply = true)
public class DeviceTokenConverter implements AttributeConverter<DeviceToken, String> {

    @Override
    public String convertToDatabaseColumn(DeviceToken attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public DeviceToken convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new DeviceToken(dbData);
    }
}
