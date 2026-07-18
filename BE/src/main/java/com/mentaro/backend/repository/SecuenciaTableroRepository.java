package com.mentaro.backend.repository;

import com.mentaro.backend.entity.SecuenciaTablero;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SecuenciaTableroRepository extends JpaRepository<SecuenciaTablero, UUID> {

    // Usado por GET /sesion, POST /responder (para ubicar el elemento activo
    // via posicion_actual) y para calcular gaps de refuerzo: trae los N
    // elementos siguientes a una posicion dada, en orden. Las posiciones
    // tienen huecos, asi que no son necesariamente consecutivas.
    List<SecuenciaTablero> findByDocumento_IdAndPosicionGreaterThanEqualOrderByPosicionAsc(
            UUID documentoId, int posicion, Pageable pageable);

    // Usado por SecuenciaTableroService para saber donde seguir agregando:
    // la primera vez que se construye la secuencia de un documento, o al
    // "profundizar" una seccion ya jugable (se agrega despues de lo ultimo).
    Optional<SecuenciaTablero> findFirstByDocumento_IdOrderByPosicionDesc(UUID documentoId);

    // Borrado en un solo DELETE para DocumentoEliminacionService.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM SecuenciaTablero s WHERE s.documento.id = :documentoId")
    int deleteByDocumento_Id(UUID documentoId);
}
