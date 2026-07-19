package com.mentaro.backend.service;

import java.util.List;
import java.util.UUID;

// Lo que se le manda a DeepSeek como prompt de usuario: el esqueleto de las
// unidades a generar (ids reales de la base, no strings inventados como en
// la Pasada A) mas el texto fuente correspondiente.
record SolicitudPasadaB(String textoFuente, List<UnidadEntrada> unidades) {

    record UnidadEntrada(
            UUID id, String titulo, UUID seccionId, String tipoContenido, List<UUID> dependeDe,
            List<UUID> imagenesEsenciales) {
    }
}
