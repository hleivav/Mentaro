package com.mentaro.backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TipoContenidoConverter implements AttributeConverter<TipoContenido, String> {

    @Override
    public String convertToDatabaseColumn(TipoContenido attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public TipoContenido convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TipoContenido.valueOf(dbData.toUpperCase());
    }
}
