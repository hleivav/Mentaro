package com.mentaro.backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class EstadoDocumentoConverter implements AttributeConverter<EstadoDocumento, String> {

    @Override
    public String convertToDatabaseColumn(EstadoDocumento attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public EstadoDocumento convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EstadoDocumento.valueOf(dbData.toUpperCase());
    }
}
