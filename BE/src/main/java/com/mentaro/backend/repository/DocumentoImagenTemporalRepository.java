package com.mentaro.backend.repository;

import com.mentaro.backend.entity.DocumentoImagenTemporal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DocumentoImagenTemporalRepository extends JpaRepository<DocumentoImagenTemporal, UUID> {

    List<DocumentoImagenTemporal> findByDocumentoIdOrderByPaginaAscOrdenAsc(UUID documentoId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM DocumentoImagenTemporal i WHERE i.actualizadoEn < :limite")
    int deleteByActualizadoEnBefore(Instant limite);
}
