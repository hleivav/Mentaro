package com.mentaro.backend.dto;

import java.util.List;

// fraccionAvance: posicion del usuario sobre el total de la secuencia
// jugable (para el Camino de Tinta - avanza con cada respuesta, acierte o
// no, igual que el puntero de sesion). unidadesDominadas/secciones: solo
// unidades cuyo REFUERZO ya se respondio bien al menos una vez (para el
// indice iluminado - una barra de dominio real, mas estricta que "ya la vi").
public record ProgresoDocumentoResponse(
        double fraccionAvance, int unidadesTotales, int unidadesDominadas, List<ProgresoSeccionDTO> secciones) {
}
