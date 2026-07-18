package com.mentaro.backend.dto;

import java.util.UUID;

// unidadesPasadas: cuantas unidades de esta seccion ya quedaron atras del
// puntero de sesion (posicion), sin importar si se acertaron - mide
// avance, igual que fraccionAvance pero recortado a esta seccion (para
// el "progreso de la seccion actual" del Camino de Tinta).
// unidadesDominadas: cuantas se dominaron de verdad (refuerzo acertado) -
// para el indice iluminado, una barra mas estricta.
public record ProgresoSeccionDTO(UUID id, int unidadesTotales, int unidadesPasadas, int unidadesDominadas) {
}
