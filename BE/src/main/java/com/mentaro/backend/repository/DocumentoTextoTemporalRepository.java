package com.mentaro.backend.repository;

import com.mentaro.backend.entity.DocumentoTextoTemporal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DocumentoTextoTemporalRepository extends JpaRepository<DocumentoTextoTemporal, UUID> {

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM DocumentoTextoTemporal t WHERE t.actualizadoEn < :limite")
    int deleteByActualizadoEnBefore(Instant limite);
}
