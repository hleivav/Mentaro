package com.mentaro.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record GenerarRequest(@NotEmpty List<UUID> unidadIds) {
}
