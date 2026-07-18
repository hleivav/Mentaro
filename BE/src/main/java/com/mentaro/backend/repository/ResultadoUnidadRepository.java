package com.mentaro.backend.repository;

import com.mentaro.backend.entity.ResultadoUnidad;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ResultadoUnidadRepository extends JpaRepository<ResultadoUnidad, UUID> {

    Optional<ResultadoUnidad> findByUsuario_IdAndUnidad_Id(UUID usuarioId, UUID unidadId);

    // Usado por ProgresoDocumentoService para saber que unidades ya domino
    // el usuario en un documento (indice iluminado + Camino de Tinta).
    List<ResultadoUnidad> findByUsuario_IdAndUnidad_Documento_Id(UUID usuarioId, UUID documentoId);

    // Borrado en un solo DELETE (no fila por fila) para DocumentoEliminacionService -
    // sin esto, borrar el documento choca con la FK a unidades.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ResultadoUnidad r WHERE r.unidad.documento.id = :documentoId")
    int deleteByUnidad_Documento_Id(UUID documentoId);

    // Para ProgresoReinicioService: reiniciar el progreso de UN usuario en UN
    // documento, sin tocar el de otros usuarios que jueguen el mismo documento.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ResultadoUnidad r WHERE r.usuario.id = :usuarioId AND r.unidad.documento.id = :documentoId")
    int deleteByUsuario_IdAndUnidad_Documento_Id(UUID usuarioId, UUID documentoId);
}
