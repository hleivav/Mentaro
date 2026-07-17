package com.mentaro.backend.service;

import com.mentaro.backend.deepseek.DeepSeekClient;
import com.mentaro.backend.deepseek.DeepSeekOpciones;
import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.NivelImportancia;
import com.mentaro.backend.entity.Seccion;
import com.mentaro.backend.entity.TipoContenido;
import com.mentaro.backend.entity.Unidad;
import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.SeccionRepository;
import com.mentaro.backend.repository.UnidadRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// Pasada A de prompt-generacion-unidades.md: mapea la estructura del
// documento y crea el esqueleto de cada unidad (sin explicaciones ni
// preguntas todavia). Barata - corre una sola vez por documento, sin
// importar cuanto termine jugando el usuario.
@Service
public class PasadaAService {

    private static final double TEMPERATURA = 0.3;

    private static final String PROMPT_SISTEMA = """
            Eres un diseñador instruccional. Tu tarea es mapear la estructura de un
            documento y segmentarlo en unidades de aprendizaje potenciales, SIN
            generar el contenido completo de cada una todavía — esta es una pasada
            de mapeo, no de generación final.

            PASO 1 — Estructura jerárquica
            Identifica la estructura del documento (libros, capítulos, secciones,
            según corresponda al tipo de texto) y arma un árbol con esa jerarquía.
            Cada nodo tiene: "id", "titulo", "padre_id" (o null si es raíz), y
            "resumen" de una línea sobre qué trata.

            IGNORA POR COMPLETO cualquier contenido que no sea la obra en sí:
            avisos legales o de licencia, portadas, páginas de copyright del
            editor, tablas de contenido, índices, bibliografías, encabezados o
            pies de página repetidos, numeración de página, notas de
            transcripción o digitalización, y cualquier metadato editorial. Esto
            aplica sin importar el formato o la fuente del documento — no asumas
            que el ruido viene siempre en la misma forma.

            PASO 2 — Segmentación en unidades (solo esqueleto, sin contenido)
            Dentro de cada nodo hoja del árbol, identifica las unidades de
            aprendizaje atómicas que contendría (una idea explicable en 3-4
            líneas). Para cada una, entrega SOLO:
            - "id"
            - "titulo"
            - "seccion_id" (a qué nodo del árbol pertenece)
            - "tipo_contenido": "declarativo" | "procedimental" | "mixto"
            - "nivel_importancia": "esencial" | "importante" | "detalle"
                - esencial: sin esto no se entiende el tema en absoluto
                - importante: enriquece la comprensión, no es indispensable
                - detalle: dato secundario, anécdota, profundización opcional
            - "depende_de": ids de otras unidades que asume conocidas

            NO generes explicaciones ni preguntas en esta pasada. Es solo el
            esqueleto — título y clasificación, nada más.

            FORMATO DE SALIDA
            JSON con dos arreglos: "estructura" (árbol de secciones) y "unidades"
            (esqueleto de cada unidad, sin contenido). Sin texto fuera del JSON.
            """;

    private final DeepSeekClient deepSeekClient;
    private final DocumentoRepository documentoRepository;
    private final SeccionRepository seccionRepository;
    private final UnidadRepository unidadRepository;
    private final ObjectMapper objectMapper;
    private final String modelo;

    public PasadaAService(
            DeepSeekClient deepSeekClient,
            DocumentoRepository documentoRepository,
            SeccionRepository seccionRepository,
            UnidadRepository unidadRepository,
            ObjectMapper objectMapper,
            @Value("${app.deepseek.modelo-pasada-a}") String modelo) {
        this.deepSeekClient = deepSeekClient;
        this.documentoRepository = documentoRepository;
        this.seccionRepository = seccionRepository;
        this.unidadRepository = unidadRepository;
        this.objectMapper = objectMapper;
        this.modelo = modelo;
    }

    // El documento debe estar en estado PROCESANDO (recien creado). Al
    // terminar queda en MAPEADO: hay estructura + esqueleto para mostrar la
    // pantalla de seleccion, pero todavia nada jugable (eso lo hace la
    // Pasada B, solo sobre lo que el usuario elija).
    @Transactional
    public void ejecutar(Documento documento, String textoFuente) {
        if (documento.getEstado() != EstadoDocumento.PROCESANDO) {
            throw new IllegalStateException(
                    "La Pasada A solo corre sobre documentos en estado PROCESANDO (actual: "
                            + documento.getEstado() + ")");
        }

        // no-thinking: el documento de diseño pide explicitamente esta pasada
        // sin razonamiento (es barata/rapida, solo mapeo estructural).
        String respuestaJson = deepSeekClient.completar(
                DeepSeekOpciones.sinThinking(modelo, TEMPERATURA), PROMPT_SISTEMA, textoFuente);
        MapaDocumento mapa = parsear(respuestaJson);

        Map<String, Seccion> seccionesPorIdIa = new HashMap<>();
        for (MapaDocumento.SeccionEsqueleto s : mapa.estructura()) {
            seccionesPorIdIa.put(s.id(), seccionRepository.save(new Seccion(documento, null, s.titulo(), s.resumen())));
        }
        for (MapaDocumento.SeccionEsqueleto s : mapa.estructura()) {
            if (s.padreId() != null) {
                seccionesPorIdIa.get(s.id()).setPadre(seccionesPorIdIa.get(s.padreId()));
            }
        }

        Map<String, Unidad> unidadesPorIdIa = new HashMap<>();
        for (MapaDocumento.UnidadEsqueleto u : mapa.unidades()) {
            Unidad unidad = new Unidad(
                    documento,
                    seccionesPorIdIa.get(u.seccionId()),
                    u.titulo(),
                    TipoContenido.valueOf(u.tipoContenido().toUpperCase()),
                    NivelImportancia.valueOf(u.nivelImportancia().toUpperCase()));
            unidadesPorIdIa.put(u.id(), unidadRepository.save(unidad));
        }
        for (MapaDocumento.UnidadEsqueleto u : mapa.unidades()) {
            if (u.dependeDe() == null || u.dependeDe().isEmpty()) {
                continue;
            }
            UUID[] dependencias = u.dependeDe().stream()
                    .map(unidadesPorIdIa::get)
                    .filter(Objects::nonNull)
                    .map(Unidad::getId)
                    .toArray(UUID[]::new);
            unidadesPorIdIa.get(u.id()).setDependeDe(dependencias);
        }

        documento.setEstado(EstadoDocumento.MAPEADO);
        documentoRepository.save(documento);
    }

    private MapaDocumento parsear(String json) {
        try {
            return objectMapper.readValue(json, MapaDocumento.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("No se pudo parsear la respuesta de la Pasada A: " + e.getMessage(), e);
        }
    }
}
