package com.mentaro.backend.repository;

import com.mentaro.backend.entity.ProgresoUsuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgresoUsuarioRepository extends JpaRepository<ProgresoUsuario, UUID> {

    Optional<ProgresoUsuario> findByUsuario_IdAndDocumento_Id(UUID usuarioId, UUID documentoId);
}
