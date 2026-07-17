package com.mentaro.backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class EstadoResultadoConverter implements AttributeConverter<EstadoResultado, String> {

    @Override
    public String convertToDatabaseColumn(EstadoResultado attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public EstadoResultado convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EstadoResultado.valueOf(dbData.toUpperCase());
    }
}
