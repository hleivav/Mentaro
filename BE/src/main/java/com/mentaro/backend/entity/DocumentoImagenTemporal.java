package com.mentaro.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documento_imagen_temporal")
public class DocumentoImagenTemporal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "documento_id", nullable = false)
    private UUID documentoId;

    @Column(nullable = false)
    private int pagina;

    @Column(nullable = false)
    private int orden;

    @Column(nullable = false, columnDefinition = "text")
    private String descripcion;

    // Sin @Lob a proposito: en Hibernate 7, @Lob sobre byte[] mapea a "oid"
    // (large object) en Postgres, no a "bytea" - la migracion usa bytea
    // (mas simple para filas chicas como estas, sin el manejo especial de
    // large objects), asi que el mapeo debe coincidir sin esa anotacion.
    @Column(name = "imagen_bytes", nullable = false)
    private byte[] imagenBytes;

    @Column(name = "media_type", nullable = false)
    private String mediaType;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn = Instant.now();

    protected DocumentoImagenTemporal() {
    }

    public DocumentoImagenTemporal(
            UUID documentoId, int pagina, int orden, String descripcion, byte[] imagenBytes, String mediaType) {
        this.documentoId = documentoId;
        this.pagina = pagina;
        this.orden = orden;
        this.descripcion = descripcion;
        this.imagenBytes = imagenBytes;
        this.mediaType = mediaType;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentoId() {
        return documentoId;
    }

    public int getPagina() {
        return pagina;
    }

    public int getOrden() {
        return orden;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public byte[] getImagenBytes() {
        return imagenBytes;
    }

    public String getMediaType() {
        return mediaType;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public void marcarUsado() {
        this.actualizadoEn = Instant.now();
    }
}
