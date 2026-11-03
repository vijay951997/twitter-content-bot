package com.ai.xbot.entity.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ContentLanguageConverter implements AttributeConverter<ContentLanguage, String> {
    @Override
    public String convertToDatabaseColumn(ContentLanguage attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public ContentLanguage convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ContentLanguage.fromCode(dbData);
    }
}
