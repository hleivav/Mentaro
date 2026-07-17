package com.mentaro.backend.dto;

import java.util.List;

// Lo que se manda al frontend: nunca incluye el indice de la respuesta
// correcta (eso se valida solo en el backend).
public record PreguntaDTO(String enunciado, List<String> alternativas) {
}
