package com.mentaro.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documento_texto_temporal")
public class DocumentoTextoTemporal {

    @Id
    @Column(name = "documento_id")
    private UUID documentoId;

    @Column(nullable = false)
    private String texto;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn = Instant.now();

    protected DocumentoTextoTemporal() {
    }

    public DocumentoTextoTemporal(UUID documentoId, String texto) {
        this.documentoId = documentoId;
        this.texto = texto;
    }

    public UUID getDocumentoId() {
        return documentoId;
    }

    public String getTexto() {
        return texto;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public void actualizar(String texto) {
        this.texto = texto;
        this.actualizadoEn = Instant.now();
    }

    public void marcarUsado() {
        this.actualizadoEn = Instant.now();
    }
}
