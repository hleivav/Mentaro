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
import java.util.UUID;

@Entity
@Table(name = "secuencia_tablero",
        uniqueConstraints = @UniqueConstraint(columnNames = {"documento_id", "posicion"}))
public class SecuenciaTablero {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_id", nullable = false)
    private Documento documento;

    @Column(nullable = false)
    private int posicion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_id", nullable = false)
    private Unidad unidad;

    @Column(name = "tipo_elemento", nullable = false)
    private TipoElemento tipoElemento;

    protected SecuenciaTablero() {
    }

    public SecuenciaTablero(Documento documento, int posicion, Unidad unidad, TipoElemento tipoElemento) {
        this.documento = documento;
        this.posicion = posicion;
        this.unidad = unidad;
        this.tipoElemento = tipoElemento;
    }

    public UUID getId() {
        return id;
    }

    public Documento getDocumento() {
        return documento;
    }

    public int getPosicion() {
        return posicion;
    }

    public void setPosicion(int posicion) {
        this.posicion = posicion;
    }

    public Unidad getUnidad() {
        return unidad;
    }

    public TipoElemento getTipoElemento() {
        return tipoElemento;
    }
}
