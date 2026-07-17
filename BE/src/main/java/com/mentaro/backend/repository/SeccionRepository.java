package com.mentaro.backend.repository;

import com.mentaro.backend.entity.Seccion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeccionRepository extends JpaRepository<Seccion, UUID> {

    List<Seccion> findByDocumento_Id(UUID documentoId);
}
