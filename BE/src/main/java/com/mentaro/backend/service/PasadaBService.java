package com.mentaro.backend.service;

import com.mentaro.backend.deepseek.DeepSeekClient;
import com.mentaro.backend.deepseek.DeepSeekOpciones;
import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.EstadoGeneracion;
import com.mentaro.backend.entity.NivelImportancia;
import com.mentaro.backend.entity.TipoContenido;
import com.mentaro.backend.entity.Unidad;
import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.UnidadRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// Pasada B de prompt-generacion-unidades.md: genera explicaciones y
// preguntas solo para las unidades declarativas seleccionadas por el
// usuario. Corre en deepseek-v4-flash con thinking habilitado; si una
// unidad puntual falla la validacion post-generacion, se reintenta sola
// con deepseek-v4-pro (el modelo mas caro se reserva para el porcentaje
// chico que ya fallo una vez). Al final construye secuencia_tablero (ver
// SecuenciaTableroService) para lo que efectivamente quedo jugable.
@Service
public class PasadaBService {

    private static final Logger log = LoggerFactory.getLogger(PasadaBService.class);

    private static final String REASONING_EFFORT = "high";
    // Frases de 6+ palabras consecutivas iguales al texto fuente cuentan como
    // calce literal. Menos que eso casi siempre es solo un termino tecnico o
    // nombre propio sin sinonimo razonable (ej. "Rocinante", "Dulcinea del
    // Toboso", "Aldonza Lorenzo") - la propia regla 2 del prompt lo exime, y
    // un chequeo de substring completo (sin este piso) los marcaba invalidos
    // por error, disparando escalados a un modelo mas caro sin necesidad.
    private static final int PALABRAS_MINIMAS_PARA_CALCE_LITERAL = 6;
    // El documento solo dice "largo similar" sin dar un numero. En una
    // corrida real, las 8 unidades que escalaron fallaron TODAS por este
    // chequeo (nunca por calce literal ni frase prohibida), con ratios entre
    // 2.05x y 2.76x sobre contenido que, leido a mano, era perfectamente
    // razonable - 2x era demasiado estricto para la variacion natural del
    // lenguaje. 3x deja margen sobre el maximo observado.
    private static final int RATIO_MAXIMO_LARGO_ALTERNATIVAS = 3;
    private static final List<String> FRASES_PROHIBIDAS = List.of(
            "todas las anteriores", "ninguna de las anteriores");

    private static final String PROMPT_SISTEMA = """
            Eres un diseñador instruccional. Recibirás una lista de unidades de
            aprendizaje ya segmentadas y clasificadas (id, titulo, seccion_id,
            tipo_contenido, depende_de) junto con el texto fuente correspondiente.
            Genera el contenido completo SOLO para las unidades cuyo
            tipo_contenido sea "declarativo".

            Para cada una, genera:
            - "explicacion_corta": 3-4 líneas, lenguaje simple, sin jerga
              innecesaria.
            - "explicacion_alternativa": la misma idea desde otro ángulo o
              analogía — no una reformulación cosmética. Se usa cuando el usuario
              falla la pregunta la primera vez.
            - "pregunta_reconocimiento": objeto con "enunciado" (string),
              "alternativas" (arreglo de 4 strings), y "correcta_index" (entero,
              0-3, la posición de la alternativa correcta dentro del arreglo).
              Apunta a la idea CENTRAL de la unidad. Para primera exposición.
            - "pregunta_refuerzo": mismo formato que "pregunta_reconocimiento"
              pero con "alternativas" de 3 strings y "correcta_index" entre 0-2.
              Prueba la misma idea en un ejemplo o contexto DISTINTO al usado en
              la explicación. Para cuando la unidad reaparece más adelante
              (refuerzo espaciado).

            REGLAS DURAS (no negociables):
            1. Los distractores deben ser errores plausibles — algo que alguien
               confundiría de verdad. Nunca opciones absurdas de relleno.
            2. Ninguna alternativa puede copiar una frase literal del texto fuente
               salvo términos técnicos sin sinónimo razonable.
            3. Todas las alternativas de una misma pregunta deben tener largo
               similar.
            4. Prohibido "todas las anteriores" / "ninguna de las anteriores".
            5. Prohibidas las dobles negaciones en el enunciado.
            6. Varía la posición de "correcta_index" entre unidades — no pongas
               la respuesta correcta siempre en la misma posición (ej. siempre 0),
               o alguien puede acertar por patrón de posición sin entender nada.

            FORMATO DE SALIDA
            Responde con un objeto JSON de la forma {"unidades": [...]} — un
            objeto con una sola clave "unidades" cuyo valor es un arreglo,
            NUNCA un arreglo JSON suelto como valor raíz. Cada elemento del
            arreglo sigue el mismo esquema de unidades ya definido, con los
            campos de contenido agregados. Sin texto fuera del JSON.
            """;

    private final DeepSeekClient deepSeekClient;
    private final DocumentoRepository documentoRepository;
    private final UnidadRepository unidadRepository;
    private final SecuenciaTableroService secuenciaTableroService;
    private final ObjectMapper objectMapper;
    private final String modeloPasadaB;
    private final String modeloEscalado;

    public PasadaBService(
            DeepSeekClient deepSeekClient,
            DocumentoRepository documentoRepository,
            UnidadRepository unidadRepository,
            SecuenciaTableroService secuenciaTableroService,
            ObjectMapper objectMapper,
            @Value("${app.deepseek.modelo-pasada-b}") String modeloPasadaB,
            @Value("${app.deepseek.modelo-pasada-b-escalado}") String modeloEscalado) {
        this.deepSeekClient = deepSeekClient;
        this.documentoRepository = documentoRepository;
        this.unidadRepository = unidadRepository;
        this.secuenciaTableroService = secuenciaTableroService;
        this.objectMapper = objectMapper;
        this.modeloPasadaB = modeloPasadaB;
        this.modeloEscalado = modeloEscalado;
    }

    // El documento debe estar en GENERANDO (primera generacion) o LISTO
    // (profundizando una seccion ya jugable). La transicion MAPEADO->GENERANDO
    // ya paso ANTES de llamar a este metodo (ver PasadaBAsyncRunner /
    // GeneracionDocumentoService) - a proposito, porque este metodo puede
    // tardar minutos (thinking + reasoning_effort=high) y al ser una sola
    // transaccion, nada de lo que hace es visible afuera hasta que retorna;
    // si el flip a GENERANDO tambien estuviera aca adentro, el polling de
    // estado nunca lo veria durante la espera larga. En el caso LISTO
    // (profundizar) el estado NO se toca - lo ya generado sigue jugable
    // mientras esto corre en el fondo, y el resultado se mergea al terminar.
    // unidadesSeleccionadas puede incluir unidades no declarativas (para
    // contexto del modelo); se ignoran al generar.
    @Transactional
    public void ejecutar(Documento documento, List<Unidad> unidadesSeleccionadas, String textoFuente) {
        boolean esPrimeraGeneracion = documento.getEstado() == EstadoDocumento.GENERANDO;
        if (!esPrimeraGeneracion && documento.getEstado() != EstadoDocumento.LISTO) {
            throw new IllegalStateException(
                    "La Pasada B solo corre sobre documentos en estado GENERANDO o LISTO (actual: "
                            + documento.getEstado() + ")");
        }

        List<Unidad> declarativas = unidadesSeleccionadas.stream()
                .filter(u -> u.getTipoContenido() == TipoContenido.DECLARATIVO)
                .toList();

        if (declarativas.isEmpty()) {
            if (esPrimeraGeneracion) {
                documento.setEstado(EstadoDocumento.LISTO);
                documentoRepository.save(documento);
            }
            return;
        }

        Map<UUID, Unidad> unidadesPorId = new HashMap<>();
        declarativas.forEach(u -> unidadesPorId.put(u.getId(), u));

        Map<UUID, ContenidoGenerado.UnidadGenerada> generado = generar(modeloPasadaB, declarativas, textoFuente);

        List<UUID> fallidas = new ArrayList<>();
        for (Unidad unidad : declarativas) {
            List<String> razones = razonesInvalidez(generado.get(unidad.getId()), textoFuente);
            if (!razones.isEmpty()) {
                fallidas.add(unidad.getId());
                log.info("Unidad {} ({}) fallo la validacion con {}: {}",
                        unidad.getId(), unidad.getTitulo(), modeloPasadaB, razones);
            }
        }

        if (!fallidas.isEmpty()) {
            log.warn("{} unidad(es) fallaron la validacion con {}, reintentando con {}: {}",
                    fallidas.size(), modeloPasadaB, modeloEscalado, fallidas);
            List<Unidad> unidadesAReintentar = fallidas.stream().map(unidadesPorId::get).toList();
            generado.putAll(generar(modeloEscalado, unidadesAReintentar, textoFuente));
        }

        // Nunca dejar contenido que fallo la propia validacion en el juego:
        // eso es exactamente lo que las reglas duras (sin "todas las
        // anteriores", sin calce literal, largo parejo) existen para evitar.
        // Excepcion: una unidad ESENCIAL que sigue fallando incluso escalada
        // se persiste igual (un hueco en algo indispensable es peor que un
        // distractor imperfecto), pero marcada para revision posterior en
        // vez de perderse en el log.
        for (Unidad unidad : declarativas) {
            ContenidoGenerado.UnidadGenerada contenido = generado.get(unidad.getId());
            List<String> razones = razonesInvalidez(contenido, textoFuente);

            if (razones.isEmpty()) {
                asignar(unidad, aleatorizarPosiciones(contenido), EstadoGeneracion.GENERADA);
            } else if (unidad.getNivelImportancia() == NivelImportancia.ESENCIAL) {
                if (contenido != null) {
                    asignar(unidad, aleatorizarPosiciones(contenido), EstadoGeneracion.FALLIDA_PERSISTIDA);
                } else {
                    unidad.marcarGeneracionFallidaExcluida();
                }
                log.warn("Unidad esencial {} ({}) {} pese a fallar la validacion incluso escalada a {}: {} - revisar.",
                        unidad.getId(), unidad.getTitulo(),
                        contenido != null ? "persistida" : "sin contenido de ningun modelo", modeloEscalado, razones);
            } else {
                unidad.marcarGeneracionFallidaExcluida();
                log.info("Unidad {} ({}, {}) excluida del juego: fallo la validacion incluso escalada a {}: {}",
                        unidad.getId(), unidad.getTitulo(), unidad.getNivelImportancia(), modeloEscalado, razones);
            }
        }
        unidadRepository.saveAll(declarativas);
        secuenciaTableroService.construir(documento, declarativas);

        if (esPrimeraGeneracion) {
            documento.setEstado(EstadoDocumento.LISTO);
            documentoRepository.save(documento);
        }
    }

    private void asignar(Unidad unidad, ContenidoGenerado.UnidadGenerada contenido, EstadoGeneracion estado) {
        unidad.asignarContenido(
                contenido.explicacionCorta(),
                contenido.explicacionAlternativa(),
                serializar(contenido.preguntaReconocimiento()),
                serializar(contenido.preguntaRefuerzo()),
                estado);
    }


    // La regla 6 (variar la posicion de correcta_index entre unidades) es
    // responsabilidad del modelo por prompt, pero en la practica no la
    // respeta de forma confiable (se observo ~65% de las respuestas
    // correctas cayendo en el mismo indice en una corrida real). En vez de
    // gastar otra llamada al modelo para corregir algo que es puramente de
    // orden de presentacion, se reordena programaticamente aca: barato,
    // determinista, y no depende de que el modelo "aprenda" a variar.
    private ContenidoGenerado.UnidadGenerada aleatorizarPosiciones(ContenidoGenerado.UnidadGenerada contenido) {
        return new ContenidoGenerado.UnidadGenerada(
                contenido.id(),
                contenido.explicacionCorta(),
                contenido.explicacionAlternativa(),
                aleatorizarPosicion(contenido.preguntaReconocimiento()),
                aleatorizarPosicion(contenido.preguntaRefuerzo()));
    }

    private ContenidoGenerado.PreguntaGenerada aleatorizarPosicion(ContenidoGenerado.PreguntaGenerada pregunta) {
        if (pregunta == null || pregunta.alternativas() == null || pregunta.alternativas().isEmpty()) {
            return pregunta;
        }

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < pregunta.alternativas().size(); i++) {
            indices.add(i);
        }
        Collections.shuffle(indices, ThreadLocalRandom.current());

        List<String> alternativasReordenadas = indices.stream().map(pregunta.alternativas()::get).toList();
        int nuevoIndex = indices.indexOf(pregunta.correctaIndex());
        return new ContenidoGenerado.PreguntaGenerada(pregunta.enunciado(), alternativasReordenadas, nuevoIndex);
    }

    private Map<UUID, ContenidoGenerado.UnidadGenerada> generar(String modelo, List<Unidad> unidades, String textoFuente) {
        String promptUsuario = construirPromptUsuario(unidades, textoFuente);
        DeepSeekOpciones opciones = DeepSeekOpciones.conThinking(modelo, REASONING_EFFORT);
        String respuestaJson = deepSeekClient.completar(opciones, PROMPT_SISTEMA, promptUsuario);

        ContenidoGenerado contenido = parsear(respuestaJson);
        Map<UUID, ContenidoGenerado.UnidadGenerada> porId = new HashMap<>();
        contenido.unidades().forEach(u -> porId.put(u.id(), u));
        return porId;
    }

    private String construirPromptUsuario(List<Unidad> unidades, String textoFuente) {
        List<SolicitudPasadaB.UnidadEntrada> entradas = unidades.stream()
                .map(u -> new SolicitudPasadaB.UnidadEntrada(
                        u.getId(),
                        u.getTitulo(),
                        u.getSeccion().getId(),
                        u.getTipoContenido().name().toLowerCase(Locale.ROOT),
                        List.of(u.getDependeDe())))
                .toList();
        return objectMapper.writeValueAsString(new SolicitudPasadaB(textoFuente, entradas));
    }

    // Validacion post-generacion sin IA (ver notas de implementacion del
    // documento): largo similar entre alternativas, sin calce literal con
    // el texto fuente, sin "todas/ninguna de las anteriores". Devuelve la
    // lista de razones especificas de falla (vacia = valido) para poder
    // diagnosticar sin adivinar por que escalo una unidad puntual.
    private List<String> razonesInvalidez(ContenidoGenerado.UnidadGenerada contenido, String textoFuente) {
        if (contenido == null) {
            return List.of("sin contenido generado por el modelo");
        }
        List<String> razones = new ArrayList<>();
        razones.addAll(razonesInvalidezPregunta("reconocimiento", contenido.preguntaReconocimiento(), textoFuente));
        razones.addAll(razonesInvalidezPregunta("refuerzo", contenido.preguntaRefuerzo(), textoFuente));
        return razones;
    }

    private List<String> razonesInvalidezPregunta(
            String tipo, ContenidoGenerado.PreguntaGenerada pregunta, String textoFuente) {
        List<String> razones = new ArrayList<>();
        if (pregunta == null || pregunta.alternativas() == null || pregunta.alternativas().isEmpty()) {
            razones.add(tipo + ": sin alternativas");
            return razones;
        }

        int minLargo = pregunta.alternativas().stream().mapToInt(String::length).min().orElse(0);
        int maxLargo = pregunta.alternativas().stream().mapToInt(String::length).max().orElse(0);
        if (minLargo > 0 && maxLargo > minLargo * RATIO_MAXIMO_LARGO_ALTERNATIVAS) {
            razones.add(tipo + ": largo dispar entre alternativas (min=" + minLargo + ", max=" + maxLargo + ")");
        }

        String textoFuenteMinusculas = textoFuente.toLowerCase(Locale.ROOT);
        for (String alternativa : pregunta.alternativas()) {
            String alternativaMinusculas = alternativa.toLowerCase(Locale.ROOT).trim();
            if (tieneCalceLiteral(alternativaMinusculas, textoFuenteMinusculas)) {
                razones.add(tipo + ": calce literal con el texto fuente en \"" + alternativa + "\"");
            }
            if (FRASES_PROHIBIDAS.stream().anyMatch(alternativaMinusculas::contains)) {
                razones.add(tipo + ": frase prohibida en \"" + alternativa + "\"");
            }
        }
        return razones;
    }

    // Calce literal = una corrida de PALABRAS_MINIMAS_PARA_CALCE_LITERAL
    // palabras consecutivas de la alternativa aparece tal cual en el texto
    // fuente. Un nombre propio suelto (1-3 palabras) nunca alcanza ese piso.
    private boolean tieneCalceLiteral(String alternativaMinusculas, String textoFuenteMinusculas) {
        String[] palabras = alternativaMinusculas.split("\\s+");
        if (palabras.length < PALABRAS_MINIMAS_PARA_CALCE_LITERAL) {
            return false;
        }
        for (int i = 0; i <= palabras.length - PALABRAS_MINIMAS_PARA_CALCE_LITERAL; i++) {
            String fragmento = String.join(" ",
                    Arrays.asList(palabras).subList(i, i + PALABRAS_MINIMAS_PARA_CALCE_LITERAL));
            if (textoFuenteMinusculas.contains(fragmento)) {
                return true;
            }
        }
        return false;
    }

    private String serializar(ContenidoGenerado.PreguntaGenerada pregunta) {
        return objectMapper.writeValueAsString(pregunta);
    }

    // El prompt pide explicito {"unidades": [...]}, pero se tolera tambien un
    // arreglo JSON suelto como raiz (el modelo lo devolvio asi alguna vez pese
    // a la instruccion) - total, ya se gastaron los tokens en generarlo.
    private ContenidoGenerado parsear(String json) {
        try {
            if (json.strip().startsWith("[")) {
                List<ContenidoGenerado.UnidadGenerada> unidades = objectMapper.readValue(json,
                        objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, ContenidoGenerado.UnidadGenerada.class));
                return new ContenidoGenerado(unidades);
            }
            return objectMapper.readValue(json, ContenidoGenerado.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("No se pudo parsear la respuesta de la Pasada B: " + e.getMessage(), e);
        }
    }
}
