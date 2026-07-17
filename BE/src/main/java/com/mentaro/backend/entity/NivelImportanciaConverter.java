package com.mentaro.backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NivelImportanciaConverter implements AttributeConverter<NivelImportancia, String> {

    @Override
    public String convertToDatabaseColumn(NivelImportancia attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public NivelImportancia convertToEntityAttribute(String dbData) {
        return dbData == null ? null : NivelImportancia.valueOf(dbData.toUpperCase());
    }
}
