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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resultado_unidad",
        uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "unidad_id"}))
public class ResultadoUnidad {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_id", nullable = false)
    private Unidad unidad;

    @Column(nullable = false)
    private EstadoResultado estado;

    @Column(nullable = false)
    private int intentos = 0;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn = Instant.now();

    protected ResultadoUnidad() {
    }

    public ResultadoUnidad(Usuario usuario, Unidad unidad, EstadoResultado estado) {
        this.usuario = usuario;
        this.unidad = unidad;
        this.estado = estado;
    }

    public UUID getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Unidad getUnidad() {
        return unidad;
    }

    public EstadoResultado getEstado() {
        return estado;
    }

    public void setEstado(EstadoResultado estado) {
        this.estado = estado;
        this.actualizadoEn = Instant.now();
    }

    public int getIntentos() {
        return intentos;
    }

    public void setIntentos(int intentos) {
        this.intentos = intentos;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
