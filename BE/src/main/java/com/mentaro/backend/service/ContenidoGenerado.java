package com.mentaro.backend.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Lo que devuelve la Pasada B para cada unidad declarativa que se le pidio
// generar.
record ContenidoGenerado(List<UnidadGenerada> unidades) {

    // preguntaReconocimiento/preguntaRefuerzo: mapa crudo, no un record de
    // esquema fijo - el "tipo" (opcion_multiple, ordenar, emparejar, ver
    // prompt-generacion-unidades.md) determina que otras claves trae cada
    // una. Se serializa tal cual para persistir (ver PasadaBService.asignar).
    record UnidadGenerada(
            UUID id, String explicacionCorta, String explicacionAlternativa,
            Map<String, Object> preguntaReconocimiento, Map<String, Object> preguntaRefuerzo) {
    }
}
