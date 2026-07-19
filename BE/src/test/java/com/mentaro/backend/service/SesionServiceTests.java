package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentaro.backend.dto.ResponderRequest;
import com.mentaro.backend.dto.ResponderResponse;
import com.mentaro.backend.dto.SesionResponse;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class SesionServiceTests {

    @Autowired
    private SesionService sesionService;
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

    private static String pregunta(int correctaIndex) {
        return "{\"tipo\": \"opcion_multiple\", \"enunciado\": \"pregunta\", \"alternativas\": [\"a\", \"b\", \"c\"], \"correcta_index\": "
                + correctaIndex + "}";
    }

    private Unidad crearUnidad(Documento documento, Seccion seccion, int correctaIndex) {
        return crearUnidadConPregunta(documento, seccion, pregunta(correctaIndex));
    }

    private Unidad crearUnidadConPregunta(Documento documento, Seccion seccion, String preguntaJson) {
        Unidad unidad = new Unidad(
                documento, seccion, "titulo", TipoContenido.DECLARATIVO, NivelImportancia.ESENCIAL);
        unidad.asignarContenido(
                "explicacion corta", "explicacion alternativa", preguntaJson, preguntaJson,
                EstadoGeneracion.GENERADA);
        return unidadRepository.save(unidad);
    }

    @Test
    void flujoCompletoDeUnaSesion() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-sesion", "sesion@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.LISTO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));

        // 8 unidades "nueva" con huecos de 100 (100, 200, ..., 800). La primera
        // responde correcto a la primera, la segunda se responde mal dos veces.
        Unidad unidad1 = crearUnidad(documento, seccion, 1);
        Unidad unidad2 = crearUnidad(documento, seccion, 1);
        int posicion = 100;
        secuenciaTableroRepository.save(new SecuenciaTablero(documento, posicion, unidad1, TipoElemento.NUEVA));
        secuenciaTableroRepository.save(new SecuenciaTablero(documento, posicion + 100, unidad2, TipoElemento.NUEVA));
        for (int i = 2; i < 8; i++) {
            Unidad relleno = crearUnidad(documento, seccion, 0);
            secuenciaTableroRepository.save(
                    new SecuenciaTablero(documento, posicion + 100 * (i + 1), relleno, TipoElemento.NUEVA));
        }

        // GET /sesion: arranca en la posicion 100, sin filtrar informacion sensible.
        SesionResponse sesionInicial = sesionService.obtenerSesion(usuario, documento.getId());
        assertThat(sesionInicial.elementos()).hasSize(8);
        assertThat(sesionInicial.elementos().getFirst().unidadId()).isEqualTo(unidad1.getId());
        assertThat(sesionInicial.elementos().getFirst().tipoElemento()).isEqualTo("nueva");
        assertThat(sesionInicial.elementos().getFirst().pregunta().get("alternativas"))
                .isEqualTo(List.of("a", "b", "c"));

        // Responder correcto a la unidad 1: avanza, queda "vista", y programa un
        // refuerzo en algun punto entre la posicion 200 y el gap largo (3-6).
        ResponderResponse r1 = sesionService.responder(usuario, documento.getId(),
                new ResponderRequest(unidad1.getId(), "nueva", "opcion_multiple", 1, 1));
        assertThat(r1.correcto()).isTrue();
        assertThat(r1.reintentar()).isNull();

        ResultadoUnidad resultado1 = resultadoUnidadRepository
                .findByUsuario_IdAndUnidad_Id(usuario.getId(), unidad1.getId()).orElseThrow();
        assertThat(resultado1.getEstado()).isEqualTo(EstadoResultado.VISTA);
        assertThat(resultado1.getIntentos()).isEqualTo(1);

        List<SecuenciaTablero> refuerzosProgramados = secuenciaTableroRepository
                .findByDocumento_IdAndPosicionGreaterThanEqualOrderByPosicionAsc(
                        documento.getId(), 200, org.springframework.data.domain.Pageable.unpaged())
                .stream().filter(e -> e.getTipoElemento() == TipoElemento.REFUERZO).toList();
        assertThat(refuerzosProgramados).hasSize(1);
        assertThat(refuerzosProgramados.getFirst().getUnidad().getId()).isEqualTo(unidad1.getId());
        // gap aleatorio 3-6 sobre las posiciones 400..900 disponibles: el punto
        // medio cae en 550, 650, 750 u 850 segun el gap sorteado.
        assertThat(refuerzosProgramados.getFirst().getPosicion()).isBetween(201, 899);

        // Responder mal a la unidad 2 (posicion 200), primer intento: no avanza.
        ResponderResponse r2 = sesionService.responder(usuario, documento.getId(),
                new ResponderRequest(unidad2.getId(), "nueva", "opcion_multiple", 0, 1));
        assertThat(r2.correcto()).isFalse();
        assertThat(r2.reintentar()).isTrue();
        assertThat(r2.explicacionAlternativa()).isEqualTo("explicacion alternativa");

        // Segundo intento, sigue mal: avanza igual (nunca bloquear), pendiente_refuerzo.
        ResponderResponse r3 = sesionService.responder(usuario, documento.getId(),
                new ResponderRequest(unidad2.getId(), "nueva", "opcion_multiple", 0, 2));
        assertThat(r3.correcto()).isFalse();
        assertThat(r3.avanzar()).isTrue();

        ResultadoUnidad resultado2 = resultadoUnidadRepository
                .findByUsuario_IdAndUnidad_Id(usuario.getId(), unidad2.getId()).orElseThrow();
        assertThat(resultado2.getEstado()).isEqualTo(EstadoResultado.PENDIENTE_REFUERZO);
        assertThat(resultado2.getIntentos()).isEqualTo(2);

        // El refuerzo de la unidad1 programado antes esta intercalado en algun
        // punto mas adelante de la secuencia (no necesariamente el siguiente
        // elemento inmediato, puede haber contenido de relleno antes). Se
        // posiciona el puntero justo ahi para probar esa rama de forma aislada,
        // en vez de asumir un orden exacto de aparicion.
        SecuenciaTablero refuerzoUnidad1 = refuerzosProgramados.getFirst();
        ProgresoUsuario progreso = progresoUsuarioRepository
                .findByUsuario_IdAndDocumento_Id(usuario.getId(), documento.getId()).orElseThrow();
        progreso.setPosicionActual(refuerzoUnidad1.getPosicion());
        progresoUsuarioRepository.save(progreso);

        SesionResponse sesionSiguiente = sesionService.obtenerSesion(usuario, documento.getId());
        var elementoRefuerzo = sesionSiguiente.elementos().getFirst();
        assertThat(elementoRefuerzo.unidadId()).isEqualTo(unidad1.getId());
        assertThat(elementoRefuerzo.tipoElemento()).isEqualTo("refuerzo");
        assertThat(elementoRefuerzo.titulo()).isNull();
        assertThat(elementoRefuerzo.explicacion()).isNull();

        ResponderResponse r4 = sesionService.responder(usuario, documento.getId(),
                new ResponderRequest(unidad1.getId(), "refuerzo", "opcion_multiple", 1, 1));
        assertThat(r4.correcto()).isTrue();

        ResultadoUnidad resultado1Final = resultadoUnidadRepository
                .findByUsuario_IdAndUnidad_Id(usuario.getId(), unidad1.getId()).orElseThrow();
        assertThat(resultado1Final.getEstado()).isEqualTo(EstadoResultado.DOMINADA);
    }

    @Test
    void fallarElUltimoRefuerzoSinNadaMasPorDelanteNoLoReprogramaNiBloqueaLaSesion() {
        // Regresion de un bug real observado en vivo: una unidad cerca del
        // final del documento, sin nada mas para intercalar, se reprogramaba
        // siempre "justo despues" del puntero - quedaba como unico elemento
        // siguiente sesion tras sesion, sin espaciado real. La sesion nunca
        // llegaba a completarse mientras el usuario siguiera fallando esa
        // unidad puntual.
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-tail", "tail@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.LISTO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));

        Unidad unidad = crearUnidad(documento, seccion, 1);
        secuenciaTableroRepository.save(new SecuenciaTablero(documento, 100, unidad, TipoElemento.REFUERZO));
        sesionService.obtenerSesion(usuario, documento.getId());

        // Falla el (unico) elemento restante de la secuencia.
        ResponderResponse respuesta = sesionService.responder(usuario, documento.getId(),
                new ResponderRequest(unidad.getId(), "refuerzo", "opcion_multiple", 0, 1));
        assertThat(respuesta.correcto()).isFalse();

        // No se reprogramo nada nuevo - nada mas alla de la fila original.
        List<SecuenciaTablero> todo = secuenciaTableroRepository
                .findByDocumento_IdAndPosicionGreaterThanEqualOrderByPosicionAsc(
                        documento.getId(), 0, org.springframework.data.domain.Pageable.unpaged());
        assertThat(todo).hasSize(1);

        // La sesion queda genuinamente completa, no atrapada repitiendo la
        // misma unidad.
        SesionResponse sesionSiguiente = sesionService.obtenerSesion(usuario, documento.getId());
        assertThat(sesionSiguiente.elementos()).isEmpty();
    }

    @Test
    void responderOrdenarConSecuenciaCorrectaEsCorrecta() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-ordenar-ok", "ordenar-ok@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.LISTO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        String preguntaOrdenar = """
                {"tipo": "ordenar", "enunciado": "Ordena", "items": ["a", "b", "c"], "orden_correcto": [2, 0, 1]}
                """;
        Unidad unidad = crearUnidadConPregunta(documento, seccion, preguntaOrdenar);
        secuenciaTableroRepository.save(new SecuenciaTablero(documento, 100, unidad, TipoElemento.NUEVA));
        sesionService.obtenerSesion(usuario, documento.getId());

        ResponderResponse respuesta = sesionService.responder(usuario, documento.getId(),
                new ResponderRequest(unidad.getId(), "nueva", "ordenar", List.of(2, 0, 1), 1));

        assertThat(respuesta.correcto()).isTrue();
    }

    @Test
    void responderOrdenarConSecuenciaIncorrectaEsIncorrecta() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-ordenar-mal", "ordenar-mal@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.LISTO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        String preguntaOrdenar = """
                {"tipo": "ordenar", "enunciado": "Ordena", "items": ["a", "b", "c"], "orden_correcto": [2, 0, 1]}
                """;
        Unidad unidad = crearUnidadConPregunta(documento, seccion, preguntaOrdenar);
        secuenciaTableroRepository.save(new SecuenciaTablero(documento, 100, unidad, TipoElemento.NUEVA));
        sesionService.obtenerSesion(usuario, documento.getId());

        ResponderResponse respuesta = sesionService.responder(usuario, documento.getId(),
                new ResponderRequest(unidad.getId(), "nueva", "ordenar", List.of(0, 1, 2), 1));

        assertThat(respuesta.correcto()).isFalse();
    }

    @Test
    void responderEmparejarConParesCorrectosEnOtroOrdenEsCorrecta() {
        // El orden en que se mandan los pares no importa, solo el conjunto -
        // ver aConjuntoDePares en SesionService.
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-emparejar-ok", "emparejar-ok@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.LISTO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        String preguntaEmparejar = """
                {"tipo": "emparejar", "enunciado": "Une", "columna_izquierda": ["x", "y", "z"],
                 "columna_derecha_desordenada": ["p", "q", "r"], "pares_correctos": [[0,1],[1,0],[2,2]]}
                """;
        Unidad unidad = crearUnidadConPregunta(documento, seccion, preguntaEmparejar);
        secuenciaTableroRepository.save(new SecuenciaTablero(documento, 100, unidad, TipoElemento.NUEVA));
        sesionService.obtenerSesion(usuario, documento.getId());

        List<List<Integer>> respuestaEnOtroOrden = List.of(List.of(2, 2), List.of(0, 1), List.of(1, 0));
        ResponderResponse respuesta = sesionService.responder(usuario, documento.getId(),
                new ResponderRequest(unidad.getId(), "nueva", "emparejar", respuestaEnOtroOrden, 1));

        assertThat(respuesta.correcto()).isTrue();
    }

    @Test
    void responderEmparejarConUnParIncorrectoEsIncorrecta() {
        Usuario usuario = usuarioRepository.save(
                new Usuario("firebase-uid-emparejar-mal", "emparejar-mal@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.LISTO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        String preguntaEmparejar = """
                {"tipo": "emparejar", "enunciado": "Une", "columna_izquierda": ["x", "y", "z"],
                 "columna_derecha_desordenada": ["p", "q", "r"], "pares_correctos": [[0,1],[1,0],[2,2]]}
                """;
        Unidad unidad = crearUnidadConPregunta(documento, seccion, preguntaEmparejar);
        secuenciaTableroRepository.save(new SecuenciaTablero(documento, 100, unidad, TipoElemento.NUEVA));
        sesionService.obtenerSesion(usuario, documento.getId());

        List<List<Integer>> respuestaConUnParMal = List.of(List.of(0, 0), List.of(1, 0), List.of(2, 2));
        ResponderResponse respuesta = sesionService.responder(usuario, documento.getId(),
                new ResponderRequest(unidad.getId(), "nueva", "emparejar", respuestaConUnParMal, 1));

        assertThat(respuesta.correcto()).isFalse();
    }

    @Test
    void sesionNuncaExponeElCampoQueRevelaLaRespuestaCorrectaSegunElTipo() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-redaccion", "redaccion@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.LISTO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        String preguntaOrdenar = """
                {"tipo": "ordenar", "enunciado": "Ordena", "items": ["a", "b", "c"], "orden_correcto": [2, 0, 1]}
                """;
        Unidad unidad = crearUnidadConPregunta(documento, seccion, preguntaOrdenar);
        secuenciaTableroRepository.save(new SecuenciaTablero(documento, 100, unidad, TipoElemento.NUEVA));

        SesionResponse sesion = sesionService.obtenerSesion(usuario, documento.getId());

        assertThat(sesion.elementos().getFirst().pregunta()).doesNotContainKey("orden_correcto");
    }

    @Test
    void responderConTipoPreguntaQueNoCoincideConElAlmacenadoEsConflicto() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-tipo-mismatch", "tipo-mismatch@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.LISTO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad unidad = crearUnidad(documento, seccion, 1);
        secuenciaTableroRepository.save(new SecuenciaTablero(documento, 100, unidad, TipoElemento.NUEVA));
        sesionService.obtenerSesion(usuario, documento.getId());

        ResponseStatusException excepcion = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> sesionService.responder(usuario, documento.getId(),
                        new ResponderRequest(unidad.getId(), "nueva", "ordenar", List.of(0, 1, 2), 1)));
        assertThat(excepcion.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void responderConFormatoDeRespuestaInvalidoParaElTipoEsBadRequest() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-formato-malo", "formato-malo@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.LISTO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad unidad = crearUnidad(documento, seccion, 1);
        secuenciaTableroRepository.save(new SecuenciaTablero(documento, 100, unidad, TipoElemento.NUEVA));
        sesionService.obtenerSesion(usuario, documento.getId());

        // opcion_multiple espera un entero, no una lista.
        ResponseStatusException excepcion = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> sesionService.responder(usuario, documento.getId(),
                        new ResponderRequest(unidad.getId(), "nueva", "opcion_multiple", List.of(0, 1), 1)));
        assertThat(excepcion.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void rechazaDocumentoDeOtroUsuario() {
        Usuario dueno = usuarioRepository.save(new Usuario("firebase-uid-dueno", "dueno@example.com"));
        Usuario intruso = usuarioRepository.save(new Usuario("firebase-uid-intruso", "intruso@example.com"));
        Documento documento = documentoRepository.save(new Documento(dueno, "Doc", EstadoDocumento.LISTO));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> sesionService.obtenerSesion(intruso, documento.getId()));
    }
}
