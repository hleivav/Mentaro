package com.mentaro.backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class EstadoGeneracionConverter implements AttributeConverter<EstadoGeneracion, String> {

    @Override
    public String convertToDatabaseColumn(EstadoGeneracion attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public EstadoGeneracion convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EstadoGeneracion.valueOf(dbData.toUpperCase());
    }
}
