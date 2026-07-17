package com.mentaro.backend.dto;

import java.util.List;
import java.util.UUID;

public record SeccionMapaDTO(UUID id, String titulo, UUID padreId, String resumen, List<UnidadMapaDTO> unidades) {
}
