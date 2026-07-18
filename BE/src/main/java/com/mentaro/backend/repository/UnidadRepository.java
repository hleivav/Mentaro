package com.mentaro.backend.repository;

import com.mentaro.backend.entity.Unidad;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UnidadRepository extends JpaRepository<Unidad, UUID> {

    List<Unidad> findByDocumento_Id(UUID documentoId);

    // Borrado en un solo DELETE para DocumentoEliminacionService.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Unidad u WHERE u.documento.id = :documentoId")
    int deleteByDocumento_Id(UUID documentoId);
}
