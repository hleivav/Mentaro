package com.mentaro.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

// respuesta: la forma varia segun tipoPregunta - un entero (indice) para
// "opcion_multiple", un arreglo de enteros para "ordenar", un arreglo de
// pares [izquierda, derecha] para "emparejar" (ver SesionService). Jackson
// lo deserializa como Integer/List<Object> segun corresponda al llegar
// como Object - la forma exacta se valida en el servicio, no aca, porque
// depende de tipoPregunta.
public record ResponderRequest(
        @NotNull UUID unidadId,
        @NotBlank String tipoElemento,
        @NotBlank String tipoPregunta,
        @NotNull Object respuesta,
        @Min(1) int intentoNumero) {
}
