package com.mentaro.backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TipoElementoConverter implements AttributeConverter<TipoElemento, String> {

    @Override
    public String convertToDatabaseColumn(TipoElemento attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public TipoElemento convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TipoElemento.valueOf(dbData.toUpperCase());
    }
}
