package com.mentaro.backend.repository;

import com.mentaro.backend.entity.Unidad;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnidadRepository extends JpaRepository<Unidad, UUID> {

    List<Unidad> findByDocumento_Id(UUID documentoId);
}
