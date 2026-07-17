package com.mentaro.backend.dto;

import java.util.UUID;

public record ElementoSesionDTO(
        UUID unidadId, String tipoElemento, String titulo, String explicacion, PreguntaDTO pregunta) {
}
