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
@Table(name = "progreso_usuario",
        uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "documento_id"}))
public class ProgresoUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_id", nullable = false)
    private Documento documento;

    @Column(name = "posicion_actual", nullable = false)
    private int posicionActual = 0;

    @Column(name = "ultima_sesion")
    private Instant ultimaSesion;

    protected ProgresoUsuario() {
    }

    public ProgresoUsuario(Usuario usuario, Documento documento) {
        this.usuario = usuario;
        this.documento = documento;
    }

    public UUID getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Documento getDocumento() {
        return documento;
    }

    public int getPosicionActual() {
        return posicionActual;
    }

    public void setPosicionActual(int posicionActual) {
        this.posicionActual = posicionActual;
    }

    public Instant getUltimaSesion() {
        return ultimaSesion;
    }

    public void setUltimaSesion(Instant ultimaSesion) {
        this.ultimaSesion = ultimaSesion;
    }
}
