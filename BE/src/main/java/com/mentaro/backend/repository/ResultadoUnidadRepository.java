package com.mentaro.backend.repository;

import com.mentaro.backend.entity.ResultadoUnidad;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultadoUnidadRepository extends JpaRepository<ResultadoUnidad, UUID> {

    Optional<ResultadoUnidad> findByUsuario_IdAndUnidad_Id(UUID usuarioId, UUID unidadId);
}
