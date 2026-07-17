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
import java.util.UUID;

@Entity
@Table(name = "secciones")
public class Seccion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_id", nullable = false)
    private Documento documento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "padre_id")
    private Seccion padre;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false, columnDefinition = "text")
    private String resumen;

    protected Seccion() {
    }

    public Seccion(Documento documento, Seccion padre, String titulo, String resumen) {
        this.documento = documento;
        this.padre = padre;
        this.titulo = titulo;
        this.resumen = resumen;
    }

    public UUID getId() {
        return id;
    }

    public Documento getDocumento() {
        return documento;
    }

    public Seccion getPadre() {
        return padre;
    }

    public void setPadre(Seccion padre) {
        this.padre = padre;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getResumen() {
        return resumen;
    }
}
