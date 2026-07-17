package com.mentaro.backend.repository;

import com.mentaro.backend.entity.Documento;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoRepository extends JpaRepository<Documento, UUID> {

    List<Documento> findByUsuario_IdOrderByCreadoEnDesc(UUID usuarioId);
}
