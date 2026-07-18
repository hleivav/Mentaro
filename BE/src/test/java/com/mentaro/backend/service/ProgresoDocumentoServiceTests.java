package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import com.mentaro.backend.dto.ProgresoDocumentoResponse;
import com.mentaro.backend.dto.ProgresoSeccionDTO;
import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.EstadoGeneracion;
import com.mentaro.backend.entity.EstadoResultado;
import com.mentaro.backend.entity.NivelImportancia;
import com.mentaro.backend.entity.ProgresoUsuario;
import com.mentaro.backend.entity.ResultadoUnidad;
import com.mentaro.backend.entity.Seccion;
import com.mentaro.backend.entity.SecuenciaTablero;
import com.mentaro.backend.entity.TipoContenido;
import com.mentaro.backend.entity.TipoElemento;
import com.mentaro.backend.entity.Unidad;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.ProgresoUsuarioRepository;
import com.mentaro.backend.repository.ResultadoUnidadRepository;
import com.mentaro.backend.repository.SeccionRepository;
import com.mentaro.backend.repository.SecuenciaTableroRepository;
import com.mentaro.backend.repository.UnidadRepository;
import com.mentaro.backend.repository.UsuarioRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class ProgresoDocumentoServiceTests {

    @Autowired
    private ProgresoDocumentoService progresoDocumentoService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private DocumentoRepository documentoRepository;
    @Autowired
    private SeccionRepository seccionRepository;
    @Autowired
    private UnidadRepository unidadRepository;
    @Autowired
    private SecuenciaTableroRepository secuenciaTableroRepository;
    @Autowired
    private ResultadoUnidadRepository resultadoUnidadRepository;
    @Autowired
    private ProgresoUsuarioRepository progresoUsuarioRepository;

    private Usuario usuario;
    private Documento documento;
    private Seccion seccion;

    private void inicializar() {
        usuario = usuarioRepository.save(new Usuario("firebase-uid-progreso-" + UUID.randomUUID(), "x@example.com"));
        documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.LISTO));
        seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
    }

    private Unidad crearUnidadJugable(String titulo) {
        Unidad unidad = new Unidad(documento, seccion, titulo, TipoContenido.DECLARATIVO, NivelImportancia.ESENCIAL);
        unidad.asignarContenido("corta", "alternativa",
                "{\"enunciado\":\"e\",\"alternativas\":[\"a\",\"b\"],\"correcta_index\":0}",
                "{\"enunciado\":\"e\",\"alternativas\":[\"a\",\"b\"],\"correcta_index\":0}",
                EstadoGeneracion.GENERADA);
        return unidadRepository.save(unidad);
    }

    private void marcarDominada(Unidad unidad) {
        ResultadoUnidad resultado = new ResultadoUnidad(usuario, unidad, EstadoResultado.DOMINADA);
        resultadoUnidadRepository.save(resultado);
    }

    @Test
    void devuelveCerosSiElUsuarioTodaviaNoJugoNinguna() {
        inicializar();
        crearUnidadJugable("U1");
        crearUnidadJugable("U2");

        ProgresoDocumentoResponse progreso = progresoDocumentoService.obtenerProgreso(usuario, documento.getId());

        assertThat(progreso.unidadesTotales()).isEqualTo(2);
        assertThat(progreso.unidadesDominadas()).isZero();
        assertThat(progreso.fraccionAvance()).isZero();
    }

    @Test
    void cuentaSoloUnidadesDominadasNoVistasNiPendientes() {
        inicializar();
        Unidad dominada = crearUnidadJugable("U1");
        Unidad vista = crearUnidadJugable("U2");
        marcarDominada(dominada);
        resultadoUnidadRepository.save(new ResultadoUnidad(usuario, vista, EstadoResultado.VISTA));

        ProgresoDocumentoResponse progreso = progresoDocumentoService.obtenerProgreso(usuario, documento.getId());

        assertThat(progreso.unidadesTotales()).isEqualTo(2);
        assertThat(progreso.unidadesDominadas()).isEqualTo(1);
    }

    @Test
    void ignoraUnidadesSinContenidoAlContarElTotal() {
        inicializar();
        crearUnidadJugable("Con contenido");
        unidadRepository.save(new Unidad(documento, seccion, "Sin contenido", TipoContenido.DECLARATIVO, NivelImportancia.DETALLE));

        ProgresoDocumentoResponse progreso = progresoDocumentoService.obtenerProgreso(usuario, documento.getId());

        assertThat(progreso.unidadesTotales()).isEqualTo(1);
    }

    @Test
    void agrupaElDominioPorSeccion() {
        inicializar();
        Seccion otraSeccion = seccionRepository.save(new Seccion(documento, null, "Otra seccion", "resumen"));

        Unidad u1 = crearUnidadJugable("U1");
        Unidad u2 = crearUnidadJugable("U2");
        Unidad u3 = new Unidad(documento, otraSeccion, "U3", TipoContenido.DECLARATIVO, NivelImportancia.ESENCIAL);
        u3.asignarContenido("corta", "alt", "{\"enunciado\":\"e\",\"alternativas\":[\"a\"],\"correcta_index\":0}",
                "{\"enunciado\":\"e\",\"alternativas\":[\"a\"],\"correcta_index\":0}", EstadoGeneracion.GENERADA);
        u3 = unidadRepository.save(u3);

        marcarDominada(u1);

        ProgresoDocumentoResponse progreso = progresoDocumentoService.obtenerProgreso(usuario, documento.getId());

        ProgresoSeccionDTO seccionOriginal = progreso.secciones().stream()
                .filter(s -> s.id().equals(seccion.getId())).findFirst().orElseThrow();
        ProgresoSeccionDTO seccionNueva = progreso.secciones().stream()
                .filter(s -> s.id().equals(otraSeccion.getId())).findFirst().orElseThrow();

        assertThat(seccionOriginal.unidadesTotales()).isEqualTo(2);
        assertThat(seccionOriginal.unidadesDominadas()).isEqualTo(1);
        assertThat(seccionNueva.unidadesTotales()).isEqualTo(1);
        assertThat(seccionNueva.unidadesDominadas()).isZero();
        assertThat(u2).isNotNull();
    }

    @Test
    void calculaLaFraccionDeAvanceComoConteoOrdinalNoPorPosicionCruda() {
        inicializar();
        Unidad u1 = crearUnidadJugable("U1");
        Unidad u2 = crearUnidadJugable("U2");
        Unidad u3 = crearUnidadJugable("U3");
        Unidad u4 = crearUnidadJugable("U4");
        // Posiciones con hueco grande a proposito (no consecutivas) - la
        // fraccion debe basarse en CUANTOS elementos quedaron atras, no en
        // el valor numerico de la posicion.
        secuenciaTableroRepository.save(new SecuenciaTablero(documento, 100, u1, TipoElemento.NUEVA));
        secuenciaTableroRepository.save(new SecuenciaTablero(documento, 200, u2, TipoElemento.NUEVA));
        secuenciaTableroRepository.save(new SecuenciaTablero(documento, 5000, u3, TipoElemento.NUEVA));
        secuenciaTableroRepository.save(new SecuenciaTablero(documento, 5100, u4, TipoElemento.NUEVA));

        ProgresoUsuario progresoUsuario = progresoUsuarioRepository.save(new ProgresoUsuario(usuario, documento));
        progresoUsuario.setPosicionActual(5000);
        progresoUsuarioRepository.save(progresoUsuario);

        ProgresoDocumentoResponse progreso = progresoDocumentoService.obtenerProgreso(usuario, documento.getId());

        // 2 de 4 elementos (100 y 200) quedaron estrictamente atras de 5000.
        assertThat(progreso.fraccionAvance()).isCloseTo(0.5, offset(0.001));
    }

    @Test
    void unidadesPasadasEsPorSeccionNoSeConfundeConElAvanceDelDocumentoEntero() {
        // Regresion del caso real reportado: seccion 1 completa (2
        // unidades), despues "profundizar" agrega seccion 2 (2 unidades
        // mas) al final. Recien arrancando seccion 2, esa seccion debe
        // mostrar 0 pasadas todavia, aunque el documento entero ya vaya
        // a mitad de camino.
        inicializar();
        Seccion seccion2 = seccionRepository.save(new Seccion(documento, null, "Seccion 2", "resumen"));

        Unidad s1u1 = crearUnidadJugable("S1U1");
        Unidad s1u2 = crearUnidadJugable("S1U2");
        secuenciaTableroRepository.save(new SecuenciaTablero(documento, 100, s1u1, TipoElemento.NUEVA));
        secuenciaTableroRepository.save(new SecuenciaTablero(documento, 200, s1u2, TipoElemento.NUEVA));

        Unidad s2u1 = new Unidad(documento, seccion2, "S2U1", TipoContenido.DECLARATIVO, NivelImportancia.ESENCIAL);
        s2u1.asignarContenido("c", "a", "{\"enunciado\":\"e\",\"alternativas\":[\"a\"],\"correcta_index\":0}",
                "{\"enunciado\":\"e\",\"alternativas\":[\"a\"],\"correcta_index\":0}", EstadoGeneracion.GENERADA);
        s2u1 = unidadRepository.save(s2u1);
        Unidad s2u2 = new Unidad(documento, seccion2, "S2U2", TipoContenido.DECLARATIVO, NivelImportancia.ESENCIAL);
        s2u2.asignarContenido("c", "a", "{\"enunciado\":\"e\",\"alternativas\":[\"a\"],\"correcta_index\":0}",
                "{\"enunciado\":\"e\",\"alternativas\":[\"a\"],\"correcta_index\":0}", EstadoGeneracion.GENERADA);
        s2u2 = unidadRepository.save(s2u2);
        secuenciaTableroRepository.save(new SecuenciaTablero(documento, 300, s2u1, TipoElemento.NUEVA));
        secuenciaTableroRepository.save(new SecuenciaTablero(documento, 400, s2u2, TipoElemento.NUEVA));

        // El puntero esta justo al arrancar seccion 2 (paso las 2 de la
        // seccion 1, ninguna de la seccion 2 todavia).
        ProgresoUsuario progresoUsuario = progresoUsuarioRepository.save(new ProgresoUsuario(usuario, documento));
        progresoUsuario.setPosicionActual(300);
        progresoUsuarioRepository.save(progresoUsuario);

        ProgresoDocumentoResponse progreso = progresoDocumentoService.obtenerProgreso(usuario, documento.getId());

        ProgresoSeccionDTO progresoSeccion1 = progreso.secciones().stream()
                .filter(s -> s.id().equals(seccion.getId())).findFirst().orElseThrow();
        ProgresoSeccionDTO progresoSeccion2 = progreso.secciones().stream()
                .filter(s -> s.id().equals(seccion2.getId())).findFirst().orElseThrow();

        assertThat(progresoSeccion1.unidadesPasadas()).isEqualTo(2);
        assertThat(progresoSeccion2.unidadesPasadas()).isZero();
        // El documento entero, en cambio, ya lleva la mitad - es
        // precisamente la diferencia que el Camino de Tinta necesita
        // mostrar por separado.
        assertThat(progreso.fraccionAvance()).isCloseTo(0.5, offset(0.001));
    }

    @Test
    void lanza403SiElDocumentoNoPerteneceAlUsuario() {
        inicializar();
        Usuario otro = usuarioRepository.save(new Usuario("firebase-uid-progreso-otro-" + UUID.randomUUID(), "y@example.com"));

        org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
                () -> progresoDocumentoService.obtenerProgreso(otro, documento.getId()));
    }
}
