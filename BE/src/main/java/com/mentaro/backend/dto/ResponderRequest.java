package com.mentaro.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ResponderRequest(
        @NotNull UUID unidadId,
        @NotBlank String tipoElemento,
        @Min(0) int respuestaIndex,
        @Min(1) int intentoNumero) {
}
