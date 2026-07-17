package com.mentaro.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documentos")
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private String titulo;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    @Column(nullable = false)
    private EstadoDocumento estado;

    protected Documento() {
    }

    public Documento(Usuario usuario, String titulo, EstadoDocumento estado) {
        this.usuario = usuario;
        this.titulo = titulo;
        this.estado = estado;
    }

    public UUID getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getTitulo() {
        return titulo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public EstadoDocumento getEstado() {
        return estado;
    }

    public void setEstado(EstadoDocumento estado) {
        this.estado = estado;
    }
}
