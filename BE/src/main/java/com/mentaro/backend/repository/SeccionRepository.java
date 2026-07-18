package com.mentaro.backend.repository;

import com.mentaro.backend.entity.Seccion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SeccionRepository extends JpaRepository<Seccion, UUID> {

    List<Seccion> findByDocumento_Id(UUID documentoId);

    // Borrado en un solo DELETE para DocumentoEliminacionService - secciones
    // se autorreferencia (padre_id), pero al borrar TODAS las del mismo
    // documento_id en una sola sentencia no hay violacion de la FK (padre e
    // hijo siempre pertenecen al mismo documento, se van juntos).
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Seccion s WHERE s.documento.id = :documentoId")
    int deleteByDocumento_Id(UUID documentoId);
}
