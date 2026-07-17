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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "unidades")
public class Unidad {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_id", nullable = false)
    private Documento documento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seccion_id", nullable = false)
    private Seccion seccion;

    @Column(nullable = false)
    private String titulo;

    @Column(name = "tipo_contenido", nullable = false)
    private TipoContenido tipoContenido;

    @Column(name = "nivel_importancia", nullable = false)
    private NivelImportancia nivelImportancia;

    // Estas 4 columnas quedan null hasta que corre la Pasada B (solo para las
    // unidades que el usuario selecciono) - ver asignarContenido/tieneContenido.
    @Column(name = "explicacion_corta", columnDefinition = "text")
    private String explicacionCorta;

    @Column(name = "explicacion_alternativa", columnDefinition = "text")
    private String explicacionAlternativa;

    // Raw JSON text; la forma exacta (enunciado, alternativas, correcta_index)
    // la define prompt-generacion-unidades.md.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pregunta_reconocimiento", columnDefinition = "jsonb")
    private String preguntaReconocimiento;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pregunta_refuerzo", columnDefinition = "jsonb")
    private String preguntaRefuerzo;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "depende_de", columnDefinition = "uuid[]")
    private UUID[] dependeDe = new UUID[0];

    // null hasta que corre la Pasada B. GENERADA = paso la validacion;
    // FALLIDA_PERSISTIDA = unidad esencial, se guardo contenido igual pese a
    // fallar la validacion (para no dejar un hueco en algo indispensable);
    // FALLIDA_EXCLUIDA = importante/detalle, fallo la validacion, se excluyo
    // del juego sin guardar contenido.
    @Column(name = "estado_generacion")
    private EstadoGeneracion estadoGeneracion;

    protected Unidad() {
    }

    // Esqueleto de la Pasada A: todavia sin contenido jugable.
    public Unidad(Documento documento, Seccion seccion, String titulo,
            TipoContenido tipoContenido, NivelImportancia nivelImportancia) {
        this.documento = documento;
        this.seccion = seccion;
        this.titulo = titulo;
        this.tipoContenido = tipoContenido;
        this.nivelImportancia = nivelImportancia;
    }

    public void asignarContenido(String explicacionCorta, String explicacionAlternativa,
            String preguntaReconocimiento, String preguntaRefuerzo, EstadoGeneracion estadoGeneracion) {
        this.explicacionCorta = explicacionCorta;
        this.explicacionAlternativa = explicacionAlternativa;
        this.preguntaReconocimiento = preguntaReconocimiento;
        this.preguntaRefuerzo = preguntaRefuerzo;
        this.estadoGeneracion = estadoGeneracion;
    }

    // Unidad importante/detalle que fallo la validacion incluso escalada: no
    // se guarda contenido, no entra al juego.
    public void marcarGeneracionFallidaExcluida() {
        this.estadoGeneracion = EstadoGeneracion.FALLIDA_EXCLUIDA;
    }

    // false hasta que corre la Pasada B para esta unidad, o si quedo
    // FALLIDA_EXCLUIDA (nunca es jugable).
    public boolean tieneContenido() {
        return explicacionCorta != null;
    }

    public EstadoGeneracion getEstadoGeneracion() {
        return estadoGeneracion;
    }

    public UUID getId() {
        return id;
    }

    public Documento getDocumento() {
        return documento;
    }

    public Seccion getSeccion() {
        return seccion;
    }

    public String getTitulo() {
        return titulo;
    }

    public TipoContenido getTipoContenido() {
        return tipoContenido;
    }

    public NivelImportancia getNivelImportancia() {
        return nivelImportancia;
    }

    public String getExplicacionCorta() {
        return explicacionCorta;
    }

    public String getExplicacionAlternativa() {
        return explicacionAlternativa;
    }

    public String getPreguntaReconocimiento() {
        return preguntaReconocimiento;
    }

    public String getPreguntaRefuerzo() {
        return preguntaRefuerzo;
    }

    public UUID[] getDependeDe() {
        return dependeDe;
    }

    public void setDependeDe(UUID[] dependeDe) {
        this.dependeDe = dependeDe;
    }
}
