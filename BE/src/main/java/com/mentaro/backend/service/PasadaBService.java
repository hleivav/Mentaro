package com.mentaro.backend.service;

import com.mentaro.backend.deepseek.DeepSeekClient;
import com.mentaro.backend.deepseek.DeepSeekOpciones;
import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.DocumentoImagenTemporal;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
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
//
// Mecanicas de pregunta - habilitadas hasta ahora en el prompt:
// opcion_multiple, ordenar, emparejar (ver "mecanicas de respuesta mas
// alla de opcion multiple"). Clasificar/completar/tocar_error quedan
// para rondas siguientes - agregarlas al prompt sin tener el resto del
// pipeline (validacion, endpoint, frontend) listo generaria contenido
// que el juego no podria presentar.
@Service
public class PasadaBService {

    private static final Logger log = LoggerFactory.getLogger(PasadaBService.class);

    private static final String REASONING_EFFORT = "high";
    // Frases de 6+ palabras consecutivas iguales al texto fuente cuentan como
    // calce literal. Menos que eso casi siempre es solo un termino tecnico o
    // nombre propio sin sinonimo razonable (ej. "Rocinante", "Dulcinea del
    // Toboso", "Aldonza Lorenzo") - la propia regla de calce literal lo
    // exime, y un chequeo de substring completo (sin este piso) los marcaba
    // invalidos por error, disparando escalados a un modelo mas caro sin
    // necesidad.
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
    private static final int ORDENAR_EMPAREJAR_MINIMO = 3;
    private static final int ORDENAR_EMPAREJAR_MAXIMO = 5;
    // Pistas textuales de orden que "ordenar" no debe traer en sus items -
    // el orden se prueba por contenido, no porque el texto ya lo delate.
    private static final List<String> PISTAS_DE_ORDEN = List.of(
            "primero", "segundo", "tercero", "cuarto", "quinto",
            "despues", "después", "luego", "finalmente", "por ultimo", "por último");

    private static final String PROMPT_SISTEMA = """
            Eres un diseñador instruccional. Recibirás una lista de unidades de
            aprendizaje ya segmentadas y clasificadas (id, titulo, seccion_id,
            tipo_contenido, depende_de, imagenes_esenciales) junto con el texto
            fuente correspondiente. Genera el contenido completo SOLO para las
            unidades cuyo tipo_contenido sea "declarativo".

            IMÁGENES ESENCIALES
            Algunas unidades traen "imagenes_esenciales": una lista de uuids de
            imágenes del documento ya confirmadas como imprescindibles para
            entender el concepto (ej. el diagrama de una situación de tránsito
            en un manual de conducir — sin verlo, la pregunta no se puede
            responder). El texto fuente ya incluye la descripción de esas
            imágenes en línea, como "[Descripción de imagen #<uuid>: ...]".
            Si, y solo si, la PREGUNTA (no solo la explicación) realmente
            necesita que el usuario vea esa imagen para responder, agregá
            "imagen_id": "<uuid>" al objeto de la pregunta (en cualquiera de
            las mecánicas de abajo), usando exactamente uno de los uuids
            listados en imagenes_esenciales de esa unidad. Nunca inventes un
            uuid que no esté en esa lista, y nunca lo agregues si la unidad no
            tiene imagenes_esenciales o si el texto ya alcanza para responder
            sin ver la imagen — es la excepción, no la norma.

            Para cada una, genera:
            - "explicacion_corta": 3-4 líneas, lenguaje simple, sin jerga
              innecesaria.
            - "explicacion_alternativa": la misma idea desde otro ángulo o
              analogía — no una reformulación cosmética. Se usa cuando el usuario
              falla la pregunta la primera vez.
            - "pregunta_reconocimiento" y "pregunta_refuerzo": preguntas para
              la primera exposición y para el repaso espaciado, respectivamente.
              Cada una puede usar una de tres mecánicas distintas — ver
              "CÓMO ELEGIR LA MECÁNICA" y "ESQUEMAS POR MECÁNICA" abajo. Por
              defecto usá la MISMA mecánica en "pregunta_refuerzo" que en
              "pregunta_reconocimiento" (es la misma idea vista de nuevo) y
              cambiá el ejemplo o contexto concreto, no la mecánica — salvo
              que el contenido puntual de esa unidad realmente pida otra cosa.

            CÓMO ELEGIR LA MECÁNICA (en este orden de prioridad)
            1. Si el contenido describe una secuencia clara de pasos o
               eventos → "ordenar".
            2. Si presenta varias relaciones o pares (concepto↔definición,
               persona↔idea, causa↔efecto) → "emparejar".
            3. Si nada de lo anterior calza con naturalidad →
               "opcion_multiple" (respaldo seguro).
            No elijas mecánica por rotar variedad — elegí la que el contenido
            de esa unidad puntual sugiera con más naturalidad. Es preferible
            tener varias unidades seguidas en "opcion_multiple" que forzar
            una mecánica que no calza.

            ESQUEMAS POR MECÁNICA
            Cada pregunta es un objeto con "tipo" (uno de los tres valores de
            abajo) más los campos específicos de ese tipo. Sin texto ni
            claves fuera de lo especificado.

            "opcion_multiple":
            {"tipo": "opcion_multiple", "enunciado": "...",
             "alternativas": ["...", "...", "...", "..."], "correcta_index": 2}
            pregunta_reconocimiento: 4 alternativas, correcta_index 0-3.
            pregunta_refuerzo: 3 alternativas, correcta_index 0-2.

            "ordenar":
            {"tipo": "ordenar", "enunciado": "Ordena los pasos del proceso",
             "items": ["Se derrite el hielo polar", "Sube el nivel del mar", "..."],
             "orden_correcto": [1, 0, 2]}
            "items" se muestra DESORDENADO al usuario (nunca en el orden
            real); "orden_correcto" son los índices de "items" en la
            secuencia real. Entre 3 y 5 items.

            "emparejar":
            {"tipo": "emparejar", "enunciado": "Une cada filósofo con su idea",
             "columna_izquierda": ["Sócrates", "Descartes", "Kant"],
             "columna_derecha_desordenada": ["Pienso, luego existo", "Mayéutica", "Imperativo categórico"],
             "pares_correctos": [[0,1],[1,0],[2,2]]}
            "pares_correctos" son índices [izquierda, derecha_desordenada].
            Entre 3 y 5 pares.

            REGLAS GENERALES (las tres mecánicas)
            A. Prohibidas las dobles negaciones en el enunciado.
            B. Todo lo evaluable (alternativas, items, columnas — según la
               mecánica) tiene que poder resolverse con la información que
               "explicacion_corta" ya dio — nunca un dato, cifra o detalle
               del texto fuente que la explicación no haya mencionado,
               aunque sea real y esté en el texto fuente. El usuario solo
               estudió la explicación corta; si la pregunta exige algo que
               no está ahí, no hay forma de responder por conocimiento
               genuino, solo por descarte o suerte.
            C. "imagen_id" es opcional y solo va en imagenes_esenciales de
               esa misma unidad (ver "IMÁGENES ESENCIALES" arriba) — nunca
               un uuid de otra unidad ni inventado.

            REGLAS PARA "opcion_multiple"
            1. Los distractores deben ser errores plausibles — algo que
               alguien confundiría de verdad. Nunca opciones absurdas de
               relleno.
            2. Ninguna alternativa puede copiar una frase literal del texto
               fuente salvo términos técnicos sin sinónimo razonable.
            3. Todas las alternativas de una misma pregunta deben tener
               largo similar.
            4. Prohibido "todas las anteriores" / "ninguna de las
               anteriores".
            5. Varía la posición de "correcta_index" entre unidades — no
               pongas la respuesta correcta siempre en la misma posición
               (ej. siempre 0), o alguien puede acertar por patrón de
               posición sin entender nada.
            6. Antes de fijar "correcta_index" en cada pregunta, releé
               "explicacion_corta" y verificá que la alternativa en ese
               índice sea la que esa explicación realmente respalda — no
               otra que suene plausible pero no esté sostenida por el
               texto. Si dudás entre dos alternativas, elegí la que se
               derive más directamente de la explicación, nunca la más
               elaborada o interesante.

            REGLAS PARA "ordenar"
            7. Los "items" no deben traer pistas de orden ya incluidas en
               el texto (ej. "primero", "1.", "después") — el orden se
               prueba por contenido, no por pistas textuales sueltas.

            REGLAS PARA "emparejar"
            8. Cada elemento de "columna_derecha_desordenada" debe poder
               confundirse razonablemente con al menos otro — si un par es
               demasiado obvio por eliminación, no prueba nada.

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
    private final DocumentoImagenTemporalService imagenTemporalService;
    private final ObjectMapper objectMapper;
    private final String modeloPasadaB;
    private final String modeloEscalado;

    public PasadaBService(
            DeepSeekClient deepSeekClient,
            DocumentoRepository documentoRepository,
            UnidadRepository unidadRepository,
            SecuenciaTableroService secuenciaTableroService,
            DocumentoImagenTemporalService imagenTemporalService,
            ObjectMapper objectMapper,
            @Value("${app.deepseek.modelo-pasada-b}") String modeloPasadaB,
            @Value("${app.deepseek.modelo-pasada-b-escalado}") String modeloEscalado) {
        this.deepSeekClient = deepSeekClient;
        this.documentoRepository = documentoRepository;
        this.unidadRepository = unidadRepository;
        this.secuenciaTableroService = secuenciaTableroService;
        this.imagenTemporalService = imagenTemporalService;
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

        Map<UUID, Set<UUID>> imagenesEsencialesPorUnidad = imagenesEsencialesPorUnidad(documento, declarativas);

        Map<UUID, ContenidoGenerado.UnidadGenerada> generado =
                generar(modeloPasadaB, declarativas, textoFuente, imagenesEsencialesPorUnidad);

        List<UUID> fallidas = new ArrayList<>();
        for (Unidad unidad : declarativas) {
            List<String> razones = razonesInvalidez(
                    generado.get(unidad.getId()), textoFuente, imagenesEsencialesPorUnidad.get(unidad.getId()));
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
            generado.putAll(generar(modeloEscalado, unidadesAReintentar, textoFuente, imagenesEsencialesPorUnidad));
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
            List<String> razones = razonesInvalidez(
                    contenido, textoFuente, imagenesEsencialesPorUnidad.get(unidad.getId()));

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

    // La regla de variar la posicion de correcta_index es responsabilidad
    // del modelo por prompt, pero en la practica no la respeta de forma
    // confiable (se observo ~65% de las respuestas correctas cayendo en el
    // mismo indice en una corrida real). En vez de gastar otra llamada al
    // modelo para corregir algo que es puramente de orden de presentacion,
    // se reordena programaticamente aca: barato, determinista, y no
    // depende de que el modelo "aprenda" a variar. Solo aplica a
    // opcion_multiple - ordenar/emparejar ya se generan desordenados por
    // diseño del esquema (ver prompt), no tienen un "indice de posicion"
    // de la respuesta correcta que reordenar.
    private ContenidoGenerado.UnidadGenerada aleatorizarPosiciones(ContenidoGenerado.UnidadGenerada contenido) {
        return new ContenidoGenerado.UnidadGenerada(
                contenido.id(),
                contenido.explicacionCorta(),
                contenido.explicacionAlternativa(),
                aleatorizarSiEsOpcionMultiple(contenido.preguntaReconocimiento()),
                aleatorizarSiEsOpcionMultiple(contenido.preguntaRefuerzo()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> aleatorizarSiEsOpcionMultiple(Map<String, Object> pregunta) {
        if (pregunta == null || !"opcion_multiple".equals(pregunta.get("tipo"))) {
            return pregunta;
        }
        Object alternativasObj = pregunta.get("alternativas");
        Object correctaObj = pregunta.get("correcta_index");
        if (!(alternativasObj instanceof List<?> alternativasRaw) || alternativasRaw.isEmpty() || correctaObj == null) {
            return pregunta;
        }
        List<String> alternativas = (List<String>) alternativasRaw;
        int correctaIndex = ((Number) correctaObj).intValue();

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < alternativas.size(); i++) {
            indices.add(i);
        }
        Collections.shuffle(indices, ThreadLocalRandom.current());

        List<String> alternativasReordenadas = indices.stream().map(alternativas::get).toList();
        int nuevoIndex = indices.indexOf(correctaIndex);

        Map<String, Object> resultado = new LinkedHashMap<>(pregunta);
        resultado.put("alternativas", alternativasReordenadas);
        resultado.put("correcta_index", nuevoIndex);
        return resultado;
    }

    // Que imagenes de cada unidad ya estan confirmadas como esenciales (ver
    // DescriptorImagenesPdf) - una sola consulta para todo el documento, no
    // una por unidad, evitando N+1. Unidad.imagenesAsociadas puede incluir
    // imagenes decorativas tambien; aca se filtran a solo las esenciales,
    // que son las unicas que la Pasada B puede ofrecer para "imagen_id".
    private Map<UUID, Set<UUID>> imagenesEsencialesPorUnidad(Documento documento, List<Unidad> unidades) {
        Set<UUID> esencialesDelDocumento = imagenTemporalService.listar(documento.getId()).stream()
                .filter(DocumentoImagenTemporal::isEsEsencial)
                .map(DocumentoImagenTemporal::getId)
                .collect(Collectors.toSet());

        Map<UUID, Set<UUID>> resultado = new HashMap<>();
        for (Unidad unidad : unidades) {
            Set<UUID> esencialesDeLaUnidad = new HashSet<>();
            for (UUID imagenId : unidad.getImagenesAsociadas()) {
                if (esencialesDelDocumento.contains(imagenId)) {
                    esencialesDeLaUnidad.add(imagenId);
                }
            }
            resultado.put(unidad.getId(), esencialesDeLaUnidad);
        }
        return resultado;
    }

    private Map<UUID, ContenidoGenerado.UnidadGenerada> generar(
            String modelo, List<Unidad> unidades, String textoFuente, Map<UUID, Set<UUID>> imagenesEsencialesPorUnidad) {
        String promptUsuario = construirPromptUsuario(unidades, textoFuente, imagenesEsencialesPorUnidad);
        DeepSeekOpciones opciones = DeepSeekOpciones.conThinking(modelo, REASONING_EFFORT);
        String respuestaJson = deepSeekClient.completar(opciones, PROMPT_SISTEMA, promptUsuario);

        ContenidoGenerado contenido = parsear(respuestaJson);
        Map<UUID, ContenidoGenerado.UnidadGenerada> porId = new HashMap<>();
        contenido.unidades().forEach(u -> porId.put(u.id(), u));
        return porId;
    }

    private String construirPromptUsuario(
            List<Unidad> unidades, String textoFuente, Map<UUID, Set<UUID>> imagenesEsencialesPorUnidad) {
        List<SolicitudPasadaB.UnidadEntrada> entradas = unidades.stream()
                .map(u -> new SolicitudPasadaB.UnidadEntrada(
                        u.getId(),
                        u.getTitulo(),
                        u.getSeccion().getId(),
                        u.getTipoContenido().name().toLowerCase(Locale.ROOT),
                        List.of(u.getDependeDe()),
                        List.copyOf(imagenesEsencialesPorUnidad.getOrDefault(u.getId(), Set.of()))))
                .toList();
        return objectMapper.writeValueAsString(new SolicitudPasadaB(textoFuente, entradas));
    }

    // Validacion post-generacion sin IA (ver notas de implementacion del
    // documento): reglas estructurales (mecanicas), chequeables sin
    // entender significado. La plausibilidad de un distractor o de un par
    // "confundible" (reglas 1 y 8 del prompt) queda fuera de lo que un
    // chequeo mecanico puede verificar - eso sigue siendo responsabilidad
    // del modelo via prompt, igual que ya pasaba con opcion_multiple.
    private List<String> razonesInvalidez(
            ContenidoGenerado.UnidadGenerada contenido, String textoFuente, Set<UUID> imagenesEsencialesValidas) {
        if (contenido == null) {
            return List.of("sin contenido generado por el modelo");
        }
        Set<UUID> validas = imagenesEsencialesValidas == null ? Set.of() : imagenesEsencialesValidas;
        List<String> razones = new ArrayList<>();
        razones.addAll(razonesInvalidezPregunta(
                "reconocimiento", contenido.preguntaReconocimiento(), textoFuente, validas));
        razones.addAll(razonesInvalidezPregunta("refuerzo", contenido.preguntaRefuerzo(), textoFuente, validas));
        return razones;
    }

    private List<String> razonesInvalidezPregunta(
            String etiqueta, Map<String, Object> pregunta, String textoFuente, Set<UUID> imagenesEsencialesValidas) {
        if (pregunta == null) {
            return List.of(etiqueta + ": sin pregunta generada");
        }
        if (!(pregunta.get("tipo") instanceof String tipo)) {
            return List.of(etiqueta + ": sin campo 'tipo' valido");
        }
        List<String> razones = new ArrayList<>(switch (tipo) {
            case "opcion_multiple" -> razonesInvalidezOpcionMultiple(etiqueta, pregunta, textoFuente);
            case "ordenar" -> razonesInvalidezOrdenar(etiqueta, pregunta);
            case "emparejar" -> razonesInvalidezEmparejar(etiqueta, pregunta);
            default -> List.of(etiqueta + ": tipo de pregunta desconocido '" + tipo + "'");
        });
        razones.addAll(razonesInvalidezImagenId(etiqueta, pregunta, imagenesEsencialesValidas));
        return razones;
    }

    // "imagen_id" es de las tres mecanicas por igual (regla general C) - se
    // valida aparte del resto de reglas especificas por tipo. Un uuid mal
    // formado o que no esta entre las imagenes_esenciales ofrecidas para esa
    // unidad es una alucinacion del modelo, no un dato confiable.
    private List<String> razonesInvalidezImagenId(
            String etiqueta, Map<String, Object> pregunta, Set<UUID> imagenesEsencialesValidas) {
        Object imagenId = pregunta.get("imagen_id");
        if (imagenId == null) {
            return List.of();
        }
        UUID id;
        try {
            id = UUID.fromString(String.valueOf(imagenId));
        } catch (IllegalArgumentException e) {
            return List.of(etiqueta + ": imagen_id '" + imagenId + "' no es un uuid valido");
        }
        if (!imagenesEsencialesValidas.contains(id)) {
            return List.of(etiqueta + ": imagen_id '" + id + "' no esta entre las imagenes_esenciales de la unidad");
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> razonesInvalidezOpcionMultiple(String etiqueta, Map<String, Object> pregunta, String textoFuente) {
        List<String> razones = new ArrayList<>();
        if (!(pregunta.get("alternativas") instanceof List<?> alternativasRaw) || alternativasRaw.isEmpty()) {
            razones.add(etiqueta + ": sin alternativas");
            return razones;
        }
        List<String> alternativas = (List<String>) alternativasRaw;

        int minLargo = alternativas.stream().mapToInt(String::length).min().orElse(0);
        int maxLargo = alternativas.stream().mapToInt(String::length).max().orElse(0);
        if (minLargo > 0 && maxLargo > minLargo * RATIO_MAXIMO_LARGO_ALTERNATIVAS) {
            razones.add(etiqueta + ": largo dispar entre alternativas (min=" + minLargo + ", max=" + maxLargo + ")");
        }

        String textoFuenteMinusculas = textoFuente.toLowerCase(Locale.ROOT);
        for (String alternativa : alternativas) {
            String alternativaMinusculas = alternativa.toLowerCase(Locale.ROOT).trim();
            if (tieneCalceLiteral(alternativaMinusculas, textoFuenteMinusculas)) {
                razones.add(etiqueta + ": calce literal con el texto fuente en \"" + alternativa + "\"");
            }
            if (FRASES_PROHIBIDAS.stream().anyMatch(alternativaMinusculas::contains)) {
                razones.add(etiqueta + ": frase prohibida en \"" + alternativa + "\"");
            }
        }
        return razones;
    }

    @SuppressWarnings("unchecked")
    private List<String> razonesInvalidezOrdenar(String etiqueta, Map<String, Object> pregunta) {
        List<String> razones = new ArrayList<>();
        if (!(pregunta.get("items") instanceof List<?> itemsRaw) || itemsRaw.isEmpty()) {
            razones.add(etiqueta + ": sin items");
            return razones;
        }
        List<String> items = (List<String>) itemsRaw;
        if (items.size() < ORDENAR_EMPAREJAR_MINIMO || items.size() > ORDENAR_EMPAREJAR_MAXIMO) {
            razones.add(etiqueta + ": ordenar debe tener entre " + ORDENAR_EMPAREJAR_MINIMO + " y "
                    + ORDENAR_EMPAREJAR_MAXIMO + " items (tiene " + items.size() + ")");
        }
        if (!(pregunta.get("orden_correcto") instanceof List<?> ordenRaw) || ordenRaw.size() != items.size()) {
            razones.add(etiqueta + ": orden_correcto ausente o de tamaño distinto a items");
        }
        for (String item : items) {
            String itemMinusculas = item.toLowerCase(Locale.ROOT);
            if (PISTAS_DE_ORDEN.stream().anyMatch(itemMinusculas::contains) || item.matches("^\\s*\\d+[.).]\\s*.*")) {
                razones.add(etiqueta + ": item con pista de orden textual en \"" + item + "\"");
            }
        }
        return razones;
    }

    private List<String> razonesInvalidezEmparejar(String etiqueta, Map<String, Object> pregunta) {
        List<String> razones = new ArrayList<>();
        boolean tieneColumnas = pregunta.get("columna_izquierda") instanceof List<?> izquierda && !izquierda.isEmpty()
                && pregunta.get("columna_derecha_desordenada") instanceof List<?> derecha && !derecha.isEmpty();
        if (!tieneColumnas) {
            razones.add(etiqueta + ": faltan columna_izquierda o columna_derecha_desordenada");
            return razones;
        }
        List<?> izquierda = (List<?>) pregunta.get("columna_izquierda");
        List<?> derecha = (List<?>) pregunta.get("columna_derecha_desordenada");
        if (izquierda.size() != derecha.size()) {
            razones.add(etiqueta + ": columna_izquierda y columna_derecha_desordenada deben tener el mismo tamaño");
        }
        if (izquierda.size() < ORDENAR_EMPAREJAR_MINIMO || izquierda.size() > ORDENAR_EMPAREJAR_MAXIMO) {
            razones.add(etiqueta + ": emparejar debe tener entre " + ORDENAR_EMPAREJAR_MINIMO + " y "
                    + ORDENAR_EMPAREJAR_MAXIMO + " pares (tiene " + izquierda.size() + ")");
        }
        if (!(pregunta.get("pares_correctos") instanceof List<?> paresRaw) || paresRaw.size() != izquierda.size()) {
            razones.add(etiqueta + ": pares_correctos ausente o de tamaño distinto a columna_izquierda");
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

    private String serializar(Map<String, Object> pregunta) {
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
