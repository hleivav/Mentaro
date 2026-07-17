package com.mentaro.backend.dto;

import com.mentaro.backend.entity.Documento;
import java.time.Instant;
import java.util.UUID;

public record DocumentoResponse(UUID id, String titulo, String estado, Instant creadoEn) {

    public static DocumentoResponse from(Documento documento) {
        return new DocumentoResponse(
                documento.getId(),
                documento.getTitulo(),
                documento.getEstado().name().toLowerCase(),
                documento.getCreadoEn());
    }
}
