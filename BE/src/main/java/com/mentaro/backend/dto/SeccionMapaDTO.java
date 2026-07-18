package com.mentaro.backend.dto;

import java.util.List;
import java.util.UUID;

// dependeDe: ids de otras secciones (nunca la propia) que contienen alguna
// unidad de la que depende una unidad de esta seccion - derivado de
// Unidad.dependeDe (ver MapaDocumentoConsultaService), no es una columna
// propia de Seccion. El frontend lo usa para auto-incluir prerrequisitos
// al seleccionar.
public record SeccionMapaDTO(
        UUID id, String titulo, UUID padreId, String resumen, List<UnidadMapaDTO> unidades, List<UUID> dependeDe) {
}
