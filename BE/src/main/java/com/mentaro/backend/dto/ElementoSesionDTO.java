package com.mentaro.backend.dto;

import java.util.Map;
import java.util.UUID;

// pregunta: la forma exacta varia segun pregunta.get("tipo") - "opcion_multiple",
// "ordenar", "emparejar" (ver prompt-generacion-unidades.md / PasadaBService).
// Siempre sin el campo que revela la respuesta correcta para ese tipo
// ("correcta_index", "orden_correcto", "pares_correctos") - eso se valida
// solo en el backend, ver SesionService.
public record ElementoSesionDTO(
        UUID unidadId, String tipoElemento, String titulo, String explicacion, Map<String, Object> pregunta) {
}
