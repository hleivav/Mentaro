package com.mentaro.backend.service;

import com.mentaro.backend.dto.ElementoSesionDTO;
import com.mentaro.backend.dto.ResponderRequest;
import com.mentaro.backend.dto.ResponderResponse;
import com.mentaro.backend.dto.SesionResponse;
import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.EstadoResultado;
import com.mentaro.backend.entity.ProgresoUsuario;
import com.mentaro.backend.entity.ResultadoUnidad;
import com.mentaro.backend.entity.SecuenciaTablero;
import com.mentaro.backend.entity.TipoElemento;
import com.mentaro.backend.entity.Unidad;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.ProgresoUsuarioRepository;
import com.mentaro.backend.repository.ResultadoUnidadRepository;
import com.mentaro.backend.repository.SecuenciaTableroRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class SesionService {

    // "gap de 3-6 unidades" / "gap corto (1-2 unidades)" del documento de
    // diseno. Las posiciones se generan con huecos (paso de 100) para poder
    // insertar un refuerzo en una posicion intermedia sin renumerar nada;
    // ver ADVERTENCIA en programarRefuerzo si un mismo hueco se agota.
    private static final int GAP_LARGO_MIN = 3;
    private static final int GAP_LARGO_MAX = 6;
    private static final int GAP_CORTO_MIN = 1;
    private static final int GAP_CORTO_MAX = 2;
    private static final int PASO_POSICION = 100;

    // Campo de cada tipo de pregunta que revela la respuesta correcta -
    // nunca se manda al frontend (ver aDto/redactar), solo se lee para
    // validar en el backend (ver esRespuestaCorrecta).
    private static final String CAMPO_RESPUESTA_OPCION_MULTIPLE = "correcta_index";
    private static final String CAMPO_RESPUESTA_ORDENAR = "orden_correcto";
    private static final String CAMPO_RESPUESTA_EMPAREJAR = "pares_correctos";

    private final DocumentoRepository documentoRepository;
    private final SecuenciaTableroRepository secuenciaTableroRepository;
    private final ProgresoUsuarioRepository progresoUsuarioRepository;
    private final ResultadoUnidadRepository resultadoUnidadRepository;
    private final ObjectMapper objectMapper;
    private final int tamanoSesion;

    public SesionService(
            DocumentoRepository documentoRepository,
            SecuenciaTableroRepository secuenciaTableroRepository,
            ProgresoUsuarioRepository progresoUsuarioRepository,
            ResultadoUnidadRepository resultadoUnidadRepository,
            ObjectMapper objectMapper,
            @Value("${app.sesion.tamano}") int tamanoSesion) {
        this.documentoRepository = documentoRepository;
        this.secuenciaTableroRepository = secuenciaTableroRepository;
        this.progresoUsuarioRepository = progresoUsuarioRepository;
        this.resultadoUnidadRepository = resultadoUnidadRepository;
        this.objectMapper = objectMapper;
        this.tamanoSesion = tamanoSesion;
    }

    @Transactional
    public SesionResponse obtenerSesion(Usuario usuario, UUID documentoId) {
        Documento documento = cargarDocumentoDe(usuario, documentoId);
        ProgresoUsuario progreso = resolverProgreso(usuario, documento);

        List<SecuenciaTablero> elementos = secuenciaTableroRepository
                .findByDocumento_IdAndPosicionGreaterThanEqualOrderByPosicionAsc(
                        documentoId, progreso.getPosicionActual(), PageRequest.of(0, tamanoSesion));

        return new SesionResponse(elementos.stream().map(this::aDto).toList());
    }

    @Transactional
    public ResponderResponse responder(Usuario usuario, UUID documentoId, ResponderRequest request) {
        Documento documento = cargarDocumentoDe(usuario, documentoId);
        ProgresoUsuario progreso = progresoUsuarioRepository
                .findByUsuario_IdAndDocumento_Id(usuario.getId(), documentoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No hay una sesion iniciada para este documento; llamar primero a GET /sesion"));

        // posicion_actual es un cursor de "reanudar desde aca", no necesariamente
        // la posicion exacta de una fila (ej. arranca en 0, y la secuencia real
        // empieza en 100) - por eso se busca con >=, igual que en obtenerSesion.
        SecuenciaTablero actual = secuenciaTableroRepository
                .findByDocumento_IdAndPosicionGreaterThanEqualOrderByPosicionAsc(
                        documentoId, progreso.getPosicionActual(), PageRequest.of(0, 1))
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No hay un elemento activo en la posicion actual de la sesion"));

        TipoElemento tipoRequest = TipoElemento.valueOf(request.tipoElemento().toUpperCase());
        if (!actual.getUnidad().getId().equals(request.unidadId()) || actual.getTipoElemento() != tipoRequest) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El elemento respondido no coincide con el que esta activo en la sesion");
        }

        Unidad unidad = actual.getUnidad();
        Map<String, Object> pregunta = parsearPregunta(preguntaDe(unidad, tipoRequest));
        boolean correcta = esRespuestaCorrecta(pregunta, request.tipoPregunta(), request.respuesta());

        ResultadoUnidad resultado = resultadoUnidadRepository
                .findByUsuario_IdAndUnidad_Id(usuario.getId(), unidad.getId())
                .orElseGet(() -> resultadoUnidadRepository.save(new ResultadoUnidad(usuario, unidad, EstadoResultado.VISTA)));
        resultado.setIntentos(resultado.getIntentos() + 1);

        return tipoRequest == TipoElemento.NUEVA
                ? responderNueva(documento, unidad, progreso, resultado, correcta, request.intentoNumero(), actual.getPosicion())
                : responderRefuerzo(documento, unidad, progreso, resultado, correcta, actual.getPosicion());
    }

    private ResponderResponse responderNueva(
            Documento documento, Unidad unidad, ProgresoUsuario progreso, ResultadoUnidad resultado,
            boolean correcta, int intentoNumero, int posicionActual) {
        if (correcta) {
            avanzarPuntero(documento.getId(), progreso, posicionActual);
            resultado.setEstado(EstadoResultado.VISTA);
            programarRefuerzo(documento, unidad, progreso.getPosicionActual(), GAP_LARGO_MIN, GAP_LARGO_MAX);
            return ResponderResponse.respuestaCorrecta();
        }
        if (intentoNumero <= 1) {
            return ResponderResponse.paraReintentar(unidad.getExplicacionAlternativa());
        }
        // Nunca bloquear el progreso: al segundo intento fallido se avanza igual.
        avanzarPuntero(documento.getId(), progreso, posicionActual);
        resultado.setEstado(EstadoResultado.PENDIENTE_REFUERZO);
        programarRefuerzo(documento, unidad, progreso.getPosicionActual(), GAP_CORTO_MIN, GAP_CORTO_MAX);
        return ResponderResponse.paraAvanzarSinBloquear();
    }

    private ResponderResponse responderRefuerzo(
            Documento documento, Unidad unidad, ProgresoUsuario progreso, ResultadoUnidad resultado,
            boolean correcta, int posicionActual) {
        ResponderResponse respuesta;
        if (correcta) {
            resultado.setEstado(EstadoResultado.DOMINADA);
            respuesta = ResponderResponse.respuestaCorrecta();
        } else {
            respuesta = ResponderResponse.paraAvanzarSinBloquear();
        }
        // Los refuerzos nunca bloquean el progreso, acierten o no.
        avanzarPuntero(documento.getId(), progreso, posicionActual);
        if (!correcta) {
            programarRefuerzo(documento, unidad, progreso.getPosicionActual(), GAP_CORTO_MIN, GAP_CORTO_MAX);
        }
        return respuesta;
    }

    private Documento cargarDocumentoDe(Usuario usuario, UUID documentoId) {
        Documento documento = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento no encontrado"));
        if (!documento.getUsuario().getId().equals(usuario.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El documento no pertenece al usuario");
        }
        if (documento.getEstado() != EstadoDocumento.LISTO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El documento todavia no esta listo (estado actual: " + documento.getEstado() + ")");
        }
        return documento;
    }

    private ProgresoUsuario resolverProgreso(Usuario usuario, Documento documento) {
        return progresoUsuarioRepository.findByUsuario_IdAndDocumento_Id(usuario.getId(), documento.getId())
                .orElseGet(() -> progresoUsuarioRepository.save(new ProgresoUsuario(usuario, documento)));
    }

    private void avanzarPuntero(UUID documentoId, ProgresoUsuario progreso, int posicionActual) {
        int siguiente = secuenciaTableroRepository
                .findByDocumento_IdAndPosicionGreaterThanEqualOrderByPosicionAsc(
                        documentoId, posicionActual + 1, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(SecuenciaTablero::getPosicion)
                // No queda nada por delante: cualquier valor mayor al actual sirve,
                // GET /sesion va a devolver una lista vacia (documento completado).
                .orElse(posicionActual + 1);
        progreso.setPosicionActual(siguiente);
        progreso.setUltimaSesion(Instant.now());
    }

    private void programarRefuerzo(Documento documento, Unidad unidad, int punteroActual, int gapMin, int gapMax) {
        int gap = ThreadLocalRandom.current().nextInt(gapMin, gapMax + 1);
        List<SecuenciaTablero> siguientes = secuenciaTableroRepository
                .findByDocumento_IdAndPosicionGreaterThanEqualOrderByPosicionAsc(
                        documento.getId(), punteroActual + 1, PageRequest.of(0, gap));

        if (siguientes.isEmpty()) {
            // No queda nada mas por delante para intercalar: programar el
            // refuerzo aca lo dejaria como UNICO elemento siguiente, sin
            // espaciado real - la sesion nunca llegaria a "completarse" si
            // el usuario sigue fallando. Se prefiere no programarlo por
            // ahora antes que atrapar al usuario en el mismo elemento
            // sesion tras sesion (nunca bloquear el progreso).
            return;
        }

        int posicionAnterior = punteroActual;
        int posicionNueva;
        if (siguientes.size() < gap) {
            // No quedan tantos elementos por delante: se agrega al final.
            posicionAnterior = siguientes.getLast().getPosicion();
            posicionNueva = posicionAnterior + PASO_POSICION;
        } else {
            SecuenciaTablero destino = siguientes.get(gap - 1);
            if (siguientes.size() >= 2) {
                posicionAnterior = siguientes.get(gap - 2).getPosicion();
            }
            // ADVERTENCIA: si el mismo hueco se bisecta muchas veces (~7+ refuerzos
            // reprogramados en el mismo tramo) esto puede colisionar con una
            // posicion existente. Con paso 100 es un caso extremo, no manejado aca.
            posicionNueva = posicionAnterior + (destino.getPosicion() - posicionAnterior) / 2;
        }

        secuenciaTableroRepository.save(new SecuenciaTablero(documento, posicionNueva, unidad, TipoElemento.REFUERZO));
    }

    private ElementoSesionDTO aDto(SecuenciaTablero elemento) {
        Unidad unidad = elemento.getUnidad();
        boolean esNueva = elemento.getTipoElemento() == TipoElemento.NUEVA;
        Map<String, Object> pregunta = parsearPregunta(preguntaDe(unidad, elemento.getTipoElemento()));

        return new ElementoSesionDTO(
                unidad.getId(),
                elemento.getTipoElemento().name().toLowerCase(),
                esNueva ? unidad.getTitulo() : null,
                esNueva ? unidad.getExplicacionCorta() : null,
                esNueva ? List.of(unidad.getImagenesAsociadas()) : List.of(),
                redactar(pregunta));
    }

    private String preguntaDe(Unidad unidad, TipoElemento tipo) {
        return tipo == TipoElemento.NUEVA ? unidad.getPreguntaReconocimiento() : unidad.getPreguntaRefuerzo();
    }

    // Nunca mandar al frontend el campo que revela la respuesta correcta -
    // cual sea ese campo depende del tipo de pregunta. "tipo" se fuerza
    // explicito (no solo se lee) porque el contenido legado sin ese campo
    // (ver tipoDe) nunca lo tenia guardado - sin esto, el frontend recibia
    // una pregunta sin tipo, mandaba tipo_pregunta=undefined al responder,
    // y el backend lo rechazaba con 400 por @NotBlank (bug real detectado
    // probando con un documento generado antes de este campo existir).
    private Map<String, Object> redactar(Map<String, Object> pregunta) {
        String tipo = tipoDe(pregunta);
        Map<String, Object> redactada = new LinkedHashMap<>(pregunta);
        redactada.put("tipo", tipo);
        redactada.remove(campoRespuestaCorrecta(tipo));
        return redactada;
    }

    // Valida la respuesta segun el tipo de pregunta almacenado - la forma
    // de "respuesta" varia (indice, arreglo de indices, arreglo de pares),
    // ver ResponderRequest. Un tipoPregunta que no coincide con el
    // realmente activo en la sesion es un 409, no un 400: el cliente esta
    // desincronizado con el estado del servidor, no mando datos invalidos
    // per se.
    private boolean esRespuestaCorrecta(Map<String, Object> pregunta, String tipoPregunta, Object respuesta) {
        String tipoAlmacenado = tipoDe(pregunta);
        if (!tipoAlmacenado.equals(tipoPregunta)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "tipo_pregunta no coincide con el que esta activo en la sesion");
        }
        try {
            return switch (tipoAlmacenado) {
                case "opcion_multiple" -> aEntero(pregunta.get(CAMPO_RESPUESTA_OPCION_MULTIPLE)) == aEntero(respuesta);
                case "ordenar" -> aListaDeEnteros(pregunta.get(CAMPO_RESPUESTA_ORDENAR)).equals(aListaDeEnteros(respuesta));
                case "emparejar" -> aConjuntoDePares(pregunta.get(CAMPO_RESPUESTA_EMPAREJAR)).equals(aConjuntoDePares(respuesta));
                default -> throw new IllegalStateException("Tipo de pregunta almacenado desconocido: " + tipoAlmacenado);
            };
        } catch (ClassCastException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato de 'respuesta' invalido para tipo_pregunta=" + tipoPregunta);
        }
    }

    // Contenido generado ANTES de que existiera el campo "tipo" (ver
    // mecanicas-respuesta.md) nunca lo tiene guardado - pero esas preguntas
    // siempre tenian la forma de opcion_multiple, porque era el unico tipo
    // que existia en ese momento. Default por compatibilidad hacia atras en
    // vez de reventar: documentos ya generados antes de este cambio siguen
    // siendo jugables sin tener que regenerarlos.
    private String tipoDe(Map<String, Object> pregunta) {
        Object tipo = pregunta.get("tipo");
        return tipo != null ? (String) tipo : "opcion_multiple";
    }

    private String campoRespuestaCorrecta(String tipo) {
        return switch (tipo) {
            case "opcion_multiple" -> CAMPO_RESPUESTA_OPCION_MULTIPLE;
            case "ordenar" -> CAMPO_RESPUESTA_ORDENAR;
            case "emparejar" -> CAMPO_RESPUESTA_EMPAREJAR;
            default -> throw new IllegalStateException("Tipo de pregunta almacenado desconocido: " + tipo);
        };
    }

    private int aEntero(Object valor) {
        return ((Number) valor).intValue();
    }

    @SuppressWarnings("unchecked")
    private List<Integer> aListaDeEnteros(Object valor) {
        return ((List<Object>) valor).stream().map(this::aEntero).toList();
    }

    @SuppressWarnings("unchecked")
    private Set<List<Integer>> aConjuntoDePares(Object valor) {
        Set<List<Integer>> conjunto = new HashSet<>();
        for (Object par : (List<Object>) valor) {
            conjunto.add(aListaDeEnteros(par));
        }
        return conjunto;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsearPregunta(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("No se pudo parsear la pregunta almacenada: " + e.getMessage(), e);
        }
    }
}
