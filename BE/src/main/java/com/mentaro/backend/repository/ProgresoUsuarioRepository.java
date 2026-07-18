package com.mentaro.backend.repository;

import com.mentaro.backend.entity.ProgresoUsuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProgresoUsuarioRepository extends JpaRepository<ProgresoUsuario, UUID> {

    Optional<ProgresoUsuario> findByUsuario_IdAndDocumento_Id(UUID usuarioId, UUID documentoId);

    // Borrado en un solo DELETE para DocumentoEliminacionService.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ProgresoUsuario p WHERE p.documento.id = :documentoId")
    int deleteByDocumento_Id(UUID documentoId);

    // Para ProgresoReinicioService: reiniciar el progreso de UN usuario en UN
    // documento, sin tocar el de otros usuarios que jueguen el mismo documento.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ProgresoUsuario p WHERE p.usuario.id = :usuarioId AND p.documento.id = :documentoId")
    int deleteByUsuario_IdAndDocumento_Id(UUID usuarioId, UUID documentoId);
}
