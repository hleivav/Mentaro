package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.mentaro.backend.deepseek.DeepSeekClient;
import com.mentaro.backend.deepseek.DeepSeekOpciones;
import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.EstadoGeneracion;
import com.mentaro.backend.entity.NivelImportancia;
import com.mentaro.backend.entity.Seccion;
import com.mentaro.backend.entity.TipoContenido;
import com.mentaro.backend.entity.Unidad;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.SeccionRepository;
import com.mentaro.backend.repository.UnidadRepository;
import com.mentaro.backend.repository.UsuarioRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Transactional
class PasadaBServiceTests {

    private static final String TEXTO_FUENTE = "Un texto fuente de ejemplo, sin frases particulares que copiar.";

    @Autowired
    private PasadaBService pasadaBService;
    @MockitoBean
    private DeepSeekClient deepSeekClient;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private DocumentoRepository documentoRepository;
    @Autowired
    private SeccionRepository seccionRepository;
    @Autowired
    private UnidadRepository unidadRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Value("${app.deepseek.modelo-pasada-b}")
    private String modeloPasadaB;
    @Value("${app.deepseek.modelo-pasada-b-escalado}")
    private String modeloEscalado;

    private Unidad crearEsqueleto(Documento documento, Seccion seccion, TipoContenido tipo) {
        return crearEsqueleto(documento, seccion, tipo, NivelImportancia.ESENCIAL);
    }

    private Unidad crearEsqueleto(
            Documento documento, Seccion seccion, TipoContenido tipo, NivelImportancia nivelImportancia) {
        return unidadRepository.save(new Unidad(documento, seccion, "Titulo unidad", tipo, nivelImportancia));
    }

    private static String respuestaValida(UUID id) {
        return """
                {"unidades": [{
                  "id": "%s",
                  "explicacion_corta": "Explicacion corta de prueba.",
                  "explicacion_alternativa": "Otra forma de explicar lo mismo.",
                  "pregunta_reconocimiento": {"tipo": "opcion_multiple", "enunciado": "¿Cual es la idea?", "alternativas": ["Opcion A valida", "Opcion B valida", "Opcion C valida", "Opcion D valida"], "correcta_index": 2},
                  "pregunta_refuerzo": {"tipo": "opcion_multiple", "enunciado": "¿Y en otro contexto?", "alternativas": ["Otra A", "Otra B", "Otra C"], "correcta_index": 1}
                }]}
                """.formatted(id);
    }

    private static String respuestaInvalida(UUID id) {
        return """
                {"unidades": [%s]}
                """.formatted(contenidoJson(id, true));
    }

    @Test
    void toleraUnArregloJsonSueltoComoRaizAunqueElPromptPidaUnObjeto() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-b8", "b8@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.GENERANDO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad unidad = crearEsqueleto(documento, seccion, TipoContenido.DECLARATIVO);

        // DeepSeek devolvio esto alguna vez en la practica pese a que el
        // prompt pide {"unidades": [...]} explicito.
        when(deepSeekClient.completar(any(DeepSeekOpciones.class), anyString(), anyString()))
                .thenReturn("[" + contenidoJson(unidad.getId(), false) + "]");

        pasadaBService.ejecutar(documento, List.of(unidad), TEXTO_FUENTE);

        Unidad actualizada = unidadRepository.findById(unidad.getId()).orElseThrow();
        assertThat(actualizada.tieneContenido()).isTrue();
        assertThat(actualizada.getEstadoGeneracion()).isEqualTo(EstadoGeneracion.GENERADA);
    }

    @Test
    void generaContenidoParaDeclarativasYQuedaListoDesdeGenerando() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-b1", "b1@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.GENERANDO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad unidad = crearEsqueleto(documento, seccion, TipoContenido.DECLARATIVO);

        when(deepSeekClient.completar(any(DeepSeekOpciones.class), anyString(), anyString()))
                .thenReturn(respuestaValida(unidad.getId()));

        pasadaBService.ejecutar(documento, List.of(unidad), TEXTO_FUENTE);

        Unidad actualizada = unidadRepository.findById(unidad.getId()).orElseThrow();
        assertThat(actualizada.tieneContenido()).isTrue();
        assertThat(actualizada.getExplicacionCorta()).isEqualTo("Explicacion corta de prueba.");
        // La posicion de correcta_index se aleatoriza al persistir (ver
        // aleatorizarPosiciones), asi que no se puede asumir un indice fijo -
        // se verifica que el indice guardado siga apuntando a la alternativa
        // que realmente era la correcta.
        Map<String, Object> pregunta = leerPregunta(actualizada.getPreguntaReconocimiento());
        @SuppressWarnings("unchecked")
        List<String> alternativas = (List<String>) pregunta.get("alternativas");
        int correctaIndex = ((Number) pregunta.get("correcta_index")).intValue();
        assertThat(alternativas.get(correctaIndex)).isEqualTo("Opcion C valida");
        assertThat(actualizada.getEstadoGeneracion()).isEqualTo(EstadoGeneracion.GENERADA);

        Documento documentoFinal = documentoRepository.findById(documento.getId()).orElseThrow();
        assertThat(documentoFinal.getEstado()).isEqualTo(EstadoDocumento.LISTO);
    }

    @Test
    void reintentaConModeloEscaladoSoloLaUnidadQueFallaValidacion() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-b2", "b2@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.GENERANDO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad unidadOk = crearEsqueleto(documento, seccion, TipoContenido.DECLARATIVO);
        Unidad unidadFalla = crearEsqueleto(documento, seccion, TipoContenido.DECLARATIVO);

        when(deepSeekClient.completar(any(DeepSeekOpciones.class), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    DeepSeekOpciones opciones = invocation.getArgument(0);
                    if (opciones.modelo().equals(modeloPasadaB)) {
                        // Simula una respuesta batch: la primera unidad sale bien, la
                        // segunda con "todas las anteriores" (invalida).
                        return """
                                {"unidades": [%s, %s]}
                                """.formatted(
                                unidadJson(unidadOk.getId(), false), unidadJson(unidadFalla.getId(), true));
                    }
                    if (opciones.modelo().equals(modeloEscalado)) {
                        return respuestaValida(unidadFalla.getId());
                    }
                    throw new IllegalArgumentException("modelo inesperado: " + opciones.modelo());
                });

        pasadaBService.ejecutar(documento, List.of(unidadOk, unidadFalla), TEXTO_FUENTE);

        Unidad okActualizada = unidadRepository.findById(unidadOk.getId()).orElseThrow();
        Unidad fallaActualizada = unidadRepository.findById(unidadFalla.getId()).orElseThrow();
        assertThat(okActualizada.tieneContenido()).isTrue();
        assertThat(fallaActualizada.tieneContenido()).isTrue();
        assertThat(fallaActualizada.getPreguntaReconocimiento()).doesNotContainIgnoringCase("todas las anteriores");
        assertThat(okActualizada.getEstadoGeneracion()).isEqualTo(EstadoGeneracion.GENERADA);
        assertThat(fallaActualizada.getEstadoGeneracion()).isEqualTo(EstadoGeneracion.GENERADA);

        Documento documentoFinal = documentoRepository.findById(documento.getId()).orElseThrow();
        assertThat(documentoFinal.getEstado()).isEqualTo(EstadoDocumento.LISTO);
    }

    @Test
    void unidadEsencialQueFallaInclusoEscaladaSePersisteConFlagParaRevision() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-b6", "b6@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.GENERANDO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad unidad = crearEsqueleto(documento, seccion, TipoContenido.DECLARATIVO, NivelImportancia.ESENCIAL);

        // Ambos modelos (flash y escalado) devuelven contenido invalido.
        when(deepSeekClient.completar(any(DeepSeekOpciones.class), anyString(), anyString()))
                .thenReturn(respuestaInvalida(unidad.getId()));

        pasadaBService.ejecutar(documento, List.of(unidad), TEXTO_FUENTE);

        Unidad actualizada = unidadRepository.findById(unidad.getId()).orElseThrow();
        // Nunca dejar un hueco en algo esencial: se persiste igual...
        assertThat(actualizada.tieneContenido()).isTrue();
        // ...pero marcada, no perdida en un log.
        assertThat(actualizada.getEstadoGeneracion()).isEqualTo(EstadoGeneracion.FALLIDA_PERSISTIDA);

        Documento documentoFinal = documentoRepository.findById(documento.getId()).orElseThrow();
        assertThat(documentoFinal.getEstado()).isEqualTo(EstadoDocumento.LISTO);
    }

    @Test
    void unidadImportanteQueFallaInclusoEscaladaSeExcluyeDelJuego() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-b7", "b7@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.GENERANDO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad unidad = crearEsqueleto(documento, seccion, TipoContenido.DECLARATIVO, NivelImportancia.IMPORTANTE);

        when(deepSeekClient.completar(any(DeepSeekOpciones.class), anyString(), anyString()))
                .thenReturn(respuestaInvalida(unidad.getId()));

        pasadaBService.ejecutar(documento, List.of(unidad), TEXTO_FUENTE);

        Unidad actualizada = unidadRepository.findById(unidad.getId()).orElseThrow();
        // Bajo costo: no vale mostrar contenido de calidad dudosa por algo
        // opcional, se excluye del juego sin guardar contenido.
        assertThat(actualizada.tieneContenido()).isFalse();
        assertThat(actualizada.getEstadoGeneracion()).isEqualTo(EstadoGeneracion.FALLIDA_EXCLUIDA);

        // El documento igual queda LISTO - una unidad excluida no bloquea el
        // resto del contenido que si se genero bien.
        Documento documentoFinal = documentoRepository.findById(documento.getId()).orElseThrow();
        assertThat(documentoFinal.getEstado()).isEqualTo(EstadoDocumento.LISTO);
    }

    private static String unidadJson(UUID id, boolean invalida) {
        return invalida ? contenidoJson(id, true) : contenidoJson(id, false);
    }

    private static String contenidoJson(UUID id, boolean invalida) {
        String alternativas = invalida
                ? "[\"Todas las anteriores\", \"Opcion B\", \"Opcion C\", \"Opcion D\"]"
                : "[\"Opcion A valida\", \"Opcion B valida\", \"Opcion C valida\", \"Opcion D valida\"]";
        return """
                {
                  "id": "%s",
                  "explicacion_corta": "Explicacion corta.",
                  "explicacion_alternativa": "Otra forma.",
                  "pregunta_reconocimiento": {"tipo": "opcion_multiple", "enunciado": "pregunta", "alternativas": %s, "correcta_index": 0},
                  "pregunta_refuerzo": {"tipo": "opcion_multiple", "enunciado": "pregunta refuerzo", "alternativas": ["Otra A", "Otra B", "Otra C"], "correcta_index": 0}
                }
                """.formatted(id, alternativas);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> leerPregunta(String json) {
        return objectMapper.readValue(json, Map.class);
    }

    @Test
    void noTocaEstadoSiYaEstaListoProfundizando() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-b3", "b3@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.LISTO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad unidad = crearEsqueleto(documento, seccion, TipoContenido.DECLARATIVO);

        when(deepSeekClient.completar(any(DeepSeekOpciones.class), anyString(), anyString()))
                .thenReturn(respuestaValida(unidad.getId()));

        pasadaBService.ejecutar(documento, List.of(unidad), TEXTO_FUENTE);

        Documento documentoFinal = documentoRepository.findById(documento.getId()).orElseThrow();
        assertThat(documentoFinal.getEstado()).isEqualTo(EstadoDocumento.LISTO);
        assertThat(unidadRepository.findById(unidad.getId()).orElseThrow().tieneContenido()).isTrue();
    }

    @Test
    void ignoraUnidadesNoDeclarativas() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-b4", "b4@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.GENERANDO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad declarativa = crearEsqueleto(documento, seccion, TipoContenido.DECLARATIVO);
        Unidad procedimental = crearEsqueleto(documento, seccion, TipoContenido.PROCEDIMENTAL);

        when(deepSeekClient.completar(any(DeepSeekOpciones.class), anyString(), anyString()))
                .thenReturn(respuestaValida(declarativa.getId()));

        pasadaBService.ejecutar(documento, List.of(declarativa, procedimental), TEXTO_FUENTE);

        assertThat(unidadRepository.findById(declarativa.getId()).orElseThrow().tieneContenido()).isTrue();
        assertThat(unidadRepository.findById(procedimental.getId()).orElseThrow().tieneContenido()).isFalse();
    }

    @Test
    void rechazaDocumentosQueNoEstanGenerandoNiListos() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-b5", "b5@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.PROCESANDO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad unidad = crearEsqueleto(documento, seccion, TipoContenido.DECLARATIVO);

        assertThrows(IllegalStateException.class,
                () -> pasadaBService.ejecutar(documento, List.of(unidad), TEXTO_FUENTE));
    }

    @Test
    void nombrePropioCortoNoDisparaFalsoPositivoDeCalceLiteral() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-b9", "b9@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.GENERANDO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad unidad = crearEsqueleto(documento, seccion, TipoContenido.DECLARATIVO);

        // "Rocinante" es un nombre propio: aparece tal cual en el texto
        // fuente, pero la alternativa que lo usa tiene menos de 6 palabras -
        // no deberia contar como calce literal (regla 2 del prompt exime
        // terminos tecnicos/nombres propios sin sinonimo razonable).
        String textoConNombrePropio = "Don Quijote decidio llamar a su caballo Rocinante, un nombre que "
                + "reflejaba tanto su pasado como su nueva condicion de montura de caballero andante.";
        String respuesta = """
                {"unidades": [{
                  "id": "%s",
                  "explicacion_corta": "Explicacion corta.",
                  "explicacion_alternativa": "Otra forma.",
                  "pregunta_reconocimiento": {"tipo": "opcion_multiple", "enunciado": "pregunta", "alternativas": ["El caballo se llamaba Rocinante", "El caballo se llamaba Bucefalo", "El caballo se llamaba Babieca", "El caballo se llamaba Clavileno"], "correcta_index": 0},
                  "pregunta_refuerzo": {"tipo": "opcion_multiple", "enunciado": "pregunta refuerzo", "alternativas": ["Se llamaba Rocinante", "Se llamaba Babieca", "Se llamaba Bucefalo"], "correcta_index": 0}
                }]}
                """.formatted(unidad.getId());

        when(deepSeekClient.completar(any(DeepSeekOpciones.class), anyString(), anyString()))
                .thenReturn(respuesta);

        pasadaBService.ejecutar(documento, List.of(unidad), textoConNombrePropio);

        assertThat(unidadRepository.findById(unidad.getId()).orElseThrow().getEstadoGeneracion())
                .isEqualTo(EstadoGeneracion.GENERADA);
    }

    @Test
    void copiaLiteralDeSeisPalabrasOMasSigueSiendoInvalida() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-b10", "b10@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.GENERANDO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad unidad = crearEsqueleto(documento, seccion, TipoContenido.DECLARATIVO, NivelImportancia.IMPORTANTE);

        String textoFuente = "El ingenioso hidalgo decidio salir de su tierra en una manana calurosa de julio "
                + "para buscar aventuras por el mundo.";
        // Copia 8 palabras consecutivas tal cual del texto fuente - debe
        // seguir siendo detectado como calce literal.
        String alternativaCopiada = "Decidio salir de su tierra en una manana calurosa de julio";
        String respuesta = """
                {"unidades": [{
                  "id": "%s",
                  "explicacion_corta": "Explicacion corta.",
                  "explicacion_alternativa": "Otra forma.",
                  "pregunta_reconocimiento": {"tipo": "opcion_multiple", "enunciado": "pregunta", "alternativas": ["%s", "Opcion B valida", "Opcion C valida", "Opcion D valida"], "correcta_index": 0},
                  "pregunta_refuerzo": {"tipo": "opcion_multiple", "enunciado": "pregunta refuerzo", "alternativas": ["Otra A", "Otra B", "Otra C"], "correcta_index": 0}
                }]}
                """.formatted(unidad.getId(), alternativaCopiada);

        when(deepSeekClient.completar(any(DeepSeekOpciones.class), anyString(), anyString()))
                .thenReturn(respuesta);

        pasadaBService.ejecutar(documento, List.of(unidad), textoFuente);

        Unidad actualizada = unidadRepository.findById(unidad.getId()).orElseThrow();
        assertThat(actualizada.tieneContenido()).isFalse();
        assertThat(actualizada.getEstadoGeneracion()).isEqualTo(EstadoGeneracion.FALLIDA_EXCLUIDA);
    }

    @Test
    void aleatorizaLaPosicionDeLaRespuestaCorrectaEnVezDeDejarlaFija() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-b11", "b11@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.GENERANDO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));

        // 20 unidades, todas con correcta_index=0 en la respuesta "cruda" del
        // modelo. Si no se aleatorizara, las 20 quedarian en indice 0 - con
        // aleatorizacion real, la probabilidad de que las 20 caigan en el
        // mismo indice por azar es (1/4)^20, practicamente cero.
        List<Unidad> unidades = new java.util.ArrayList<>();
        StringBuilder unidadesJson = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            Unidad u = crearEsqueleto(documento, seccion, TipoContenido.DECLARATIVO);
            unidades.add(u);
            if (i > 0) {
                unidadesJson.append(",");
            }
            unidadesJson.append(contenidoJson(u.getId(), false));
        }

        when(deepSeekClient.completar(any(DeepSeekOpciones.class), anyString(), anyString()))
                .thenReturn("{\"unidades\": [" + unidadesJson + "]}");

        pasadaBService.ejecutar(documento, unidades, TEXTO_FUENTE);

        long distintosDeCero = unidades.stream()
                .map(u -> unidadRepository.findById(u.getId()).orElseThrow())
                .map(u -> leerPregunta(u.getPreguntaReconocimiento()))
                .filter(p -> ((Number) p.get("correcta_index")).intValue() != 0)
                .count();

        assertThat(distintosDeCero).isGreaterThan(0);
    }

    private static String contenidoOrdenar(UUID id, String itemsJson) {
        return """
                {"unidades": [{
                  "id": "%s",
                  "explicacion_corta": "Explicacion corta.",
                  "explicacion_alternativa": "Otra forma.",
                  "pregunta_reconocimiento": {"tipo": "ordenar", "enunciado": "Ordena los pasos", "items": %s, "orden_correcto": [1, 0, 2]},
                  "pregunta_refuerzo": {"tipo": "ordenar", "enunciado": "Ordena de nuevo", "items": %s, "orden_correcto": [1, 0, 2]}
                }]}
                """.formatted(id, itemsJson, itemsJson);
    }

    private static String contenidoEmparejar(UUID id, String columnaIzquierdaJson, String columnaDerechaJson) {
        return """
                {"unidades": [{
                  "id": "%s",
                  "explicacion_corta": "Explicacion corta.",
                  "explicacion_alternativa": "Otra forma.",
                  "pregunta_reconocimiento": {"tipo": "emparejar", "enunciado": "Une cada elemento",
                    "columna_izquierda": %s, "columna_derecha_desordenada": %s, "pares_correctos": [[0,1],[1,0],[2,2]]},
                  "pregunta_refuerzo": {"tipo": "emparejar", "enunciado": "Une de nuevo",
                    "columna_izquierda": %s, "columna_derecha_desordenada": %s, "pares_correctos": [[0,1],[1,0],[2,2]]}
                }]}
                """.formatted(id, columnaIzquierdaJson, columnaDerechaJson, columnaIzquierdaJson, columnaDerechaJson);
    }

    @Test
    void ordenarValidoQuedaGenerada() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-ordenar1", "ordenar1@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.GENERANDO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad unidad = crearEsqueleto(documento, seccion, TipoContenido.DECLARATIVO);

        when(deepSeekClient.completar(any(DeepSeekOpciones.class), anyString(), anyString()))
                .thenReturn(contenidoOrdenar(unidad.getId(), "[\"Paso B\", \"Paso A\", \"Paso C\"]"));

        pasadaBService.ejecutar(documento, List.of(unidad), TEXTO_FUENTE);

        Unidad actualizada = unidadRepository.findById(unidad.getId()).orElseThrow();
        assertThat(actualizada.getEstadoGeneracion()).isEqualTo(EstadoGeneracion.GENERADA);
    }

    @Test
    void ordenarConPistaDeOrdenEnUnItemEsInvalida() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-ordenar2", "ordenar2@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.GENERANDO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad unidad = crearEsqueleto(documento, seccion, TipoContenido.DECLARATIVO, NivelImportancia.IMPORTANTE);

        when(deepSeekClient.completar(any(DeepSeekOpciones.class), anyString(), anyString()))
                .thenReturn(contenidoOrdenar(unidad.getId(),
                        "[\"Primero se hace B\", \"Luego se hace A\", \"Finalmente se hace C\"]"));

        pasadaBService.ejecutar(documento, List.of(unidad), TEXTO_FUENTE);

        Unidad actualizada = unidadRepository.findById(unidad.getId()).orElseThrow();
        assertThat(actualizada.getEstadoGeneracion()).isEqualTo(EstadoGeneracion.FALLIDA_EXCLUIDA);
    }

    @Test
    void ordenarConMenosDeTresItemsEsInvalida() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-ordenar3", "ordenar3@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.GENERANDO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad unidad = crearEsqueleto(documento, seccion, TipoContenido.DECLARATIVO, NivelImportancia.IMPORTANTE);

        when(deepSeekClient.completar(any(DeepSeekOpciones.class), anyString(), anyString()))
                .thenReturn(contenidoOrdenar(unidad.getId(), "[\"Paso B\", \"Paso A\"]"));

        pasadaBService.ejecutar(documento, List.of(unidad), TEXTO_FUENTE);

        Unidad actualizada = unidadRepository.findById(unidad.getId()).orElseThrow();
        assertThat(actualizada.getEstadoGeneracion()).isEqualTo(EstadoGeneracion.FALLIDA_EXCLUIDA);
    }

    @Test
    void emparejarValidoQuedaGenerada() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-emparejar1", "emparejar1@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.GENERANDO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad unidad = crearEsqueleto(documento, seccion, TipoContenido.DECLARATIVO);

        when(deepSeekClient.completar(any(DeepSeekOpciones.class), anyString(), anyString()))
                .thenReturn(contenidoEmparejar(unidad.getId(),
                        "[\"Elemento X\", \"Elemento Y\", \"Elemento Z\"]",
                        "[\"Definicion P\", \"Definicion Q\", \"Definicion R\"]"));

        pasadaBService.ejecutar(documento, List.of(unidad), TEXTO_FUENTE);

        Unidad actualizada = unidadRepository.findById(unidad.getId()).orElseThrow();
        assertThat(actualizada.getEstadoGeneracion()).isEqualTo(EstadoGeneracion.GENERADA);
    }

    @Test
    void emparejarConColumnasDeTamanoDistintoEsInvalida() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-emparejar2", "emparejar2@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.GENERANDO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad unidad = crearEsqueleto(documento, seccion, TipoContenido.DECLARATIVO, NivelImportancia.IMPORTANTE);

        when(deepSeekClient.completar(any(DeepSeekOpciones.class), anyString(), anyString()))
                .thenReturn(contenidoEmparejar(unidad.getId(),
                        "[\"Elemento X\", \"Elemento Y\", \"Elemento Z\"]",
                        "[\"Definicion P\", \"Definicion Q\"]"));

        pasadaBService.ejecutar(documento, List.of(unidad), TEXTO_FUENTE);

        Unidad actualizada = unidadRepository.findById(unidad.getId()).orElseThrow();
        assertThat(actualizada.getEstadoGeneracion()).isEqualTo(EstadoGeneracion.FALLIDA_EXCLUIDA);
    }

    @Test
    void aleatorizarPosicionesNoTocaOrdenarNiEmparejar() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-noalea", "noalea@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.GENERANDO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad unidad = crearEsqueleto(documento, seccion, TipoContenido.DECLARATIVO);

        when(deepSeekClient.completar(any(DeepSeekOpciones.class), anyString(), anyString()))
                .thenReturn(contenidoOrdenar(unidad.getId(), "[\"Paso B\", \"Paso A\", \"Paso C\"]"));

        pasadaBService.ejecutar(documento, List.of(unidad), TEXTO_FUENTE);

        Unidad actualizada = unidadRepository.findById(unidad.getId()).orElseThrow();
        Map<String, Object> pregunta = leerPregunta(actualizada.getPreguntaReconocimiento());
        assertThat(pregunta.get("items")).isEqualTo(List.of("Paso B", "Paso A", "Paso C"));
        assertThat(pregunta.get("orden_correcto")).isEqualTo(List.of(1, 0, 2));
    }
}
