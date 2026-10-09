package com.innovify.skillswap.subscriptionbilling.infrastructure.persistence.jpa.converters;

import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.SubscriptionStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the status as text, like the other states of the platform ("Active", "Cancelled", "Expired"). */
@Converter(autoApply = true)
public class SubscriptionStatusConverter implements AttributeConverter<SubscriptionStatus, String> {

    @Override
    public String convertToDatabaseColumn(SubscriptionStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public SubscriptionStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : SubscriptionStatus.fromValue(dbData);
    }
}
