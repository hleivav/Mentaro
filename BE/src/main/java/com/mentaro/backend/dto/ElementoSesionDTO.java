package com.mentaro.backend.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// pregunta: la forma exacta varia segun pregunta.get("tipo") - "opcion_multiple",
// "ordenar", "emparejar" (ver prompt-generacion-unidades.md / PasadaBService).
// Siempre sin el campo que revela la respuesta correcta para ese tipo
// ("correcta_index", "orden_correcto", "pares_correctos") - eso se valida
// solo en el backend, ver SesionService. "imagen_id" (si esta presente en
// pregunta) SI viaja tal cual - no revela la respuesta, solo que imagen
// mostrar junto a la pregunta.
//
// imagenesAsociadas: igual criterio que titulo/explicacion - solo para
// "nueva" (vacia en "refuerzo", donde tampoco se muestra la explicacion).
public record ElementoSesionDTO(
        UUID unidadId, String tipoElemento, String titulo, String explicacion, List<UUID> imagenesAsociadas,
        Map<String, Object> pregunta) {
}
