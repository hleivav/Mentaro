package com.mentaro.backend.service;

import java.util.List;
import java.util.UUID;

// Lo que devuelve la Pasada B para cada unidad declarativa que se le pidio
// generar.
record ContenidoGenerado(List<UnidadGenerada> unidades) {

    record UnidadGenerada(
            UUID id, String explicacionCorta, String explicacionAlternativa,
            PreguntaGenerada preguntaReconocimiento, PreguntaGenerada preguntaRefuerzo) {
    }

    // Mismo esquema {enunciado, alternativas, correcta_index} que
    // PreguntaAlmacenada, la forma en que SesionService lee las preguntas ya
    // guardadas - se serializa tal cual para persistir.
    record PreguntaGenerada(String enunciado, List<String> alternativas, int correctaIndex) {
    }
}
