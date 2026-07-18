package com.mentaro.backend.service;

import com.mentaro.backend.dto.ProgresoDocumentoResponse;
import com.mentaro.backend.dto.ProgresoSeccionDTO;
import com.mentaro.backend.entity.EstadoResultado;
import com.mentaro.backend.entity.ProgresoUsuario;
import com.mentaro.backend.entity.SecuenciaTablero;
import com.mentaro.backend.entity.TipoElemento;
import com.mentaro.backend.entity.Unidad;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.ProgresoUsuarioRepository;
import com.mentaro.backend.repository.ResultadoUnidadRepository;
import com.mentaro.backend.repository.SecuenciaTableroRepository;
import com.mentaro.backend.repository.UnidadRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

// Progreso real de un usuario en un documento, para tres lecturas
// distintas que necesitan datos distintos (ver sistema de diseño):
//   - Camino de Tinta (total): fraccionAvance sobre TODO el documento -
//     cuantos elementos de la secuencia ya se dejaron atras, acierte o
//     no. Persiste entre sesiones de "profundizar" (secciones nuevas se
//     agregan al final, no reinician el conteo).
//   - Camino de Tinta (seccion actual): unidadesPasadas/unidadesTotales
//     de la seccion que se esta jugando ahora mismo - mismo criterio de
//     avance, pero recortado, para no confundir "cuanto llevo de esta
//     seccion" con "cuanto llevo del documento entero".
//   - Indice iluminado: unidadesDominadas por seccion - solo cuenta lo
//     que el usuario realmente domino (refuerzo respondido bien), una
//     barra mucho mas estricta que "ya paso por ahi".
@Service
public class ProgresoDocumentoService {

    private final DocumentoConsultaService documentoConsultaService;
    private final UnidadRepository unidadRepository;
    private final ResultadoUnidadRepository resultadoUnidadRepository;
    private final SecuenciaTableroRepository secuenciaTableroRepository;
    private final ProgresoUsuarioRepository progresoUsuarioRepository;

    public ProgresoDocumentoService(
            DocumentoConsultaService documentoConsultaService,
            UnidadRepository unidadRepository,
            ResultadoUnidadRepository resultadoUnidadRepository,
            SecuenciaTableroRepository secuenciaTableroRepository,
            ProgresoUsuarioRepository progresoUsuarioRepository) {
        this.documentoConsultaService = documentoConsultaService;
        this.unidadRepository = unidadRepository;
        this.resultadoUnidadRepository = resultadoUnidadRepository;
        this.secuenciaTableroRepository = secuenciaTableroRepository;
        this.progresoUsuarioRepository = progresoUsuarioRepository;
    }

    public ProgresoDocumentoResponse obtenerProgreso(Usuario usuario, UUID documentoId) {
        documentoConsultaService.obtener(usuario, documentoId);

        List<Unidad> jugables = unidadRepository.findByDocumento_Id(documentoId).stream()
                .filter(Unidad::tieneContenido)
                .toList();

        List<SecuenciaTablero> todos = secuenciaTableroRepository
                .findByDocumento_IdAndPosicionGreaterThanEqualOrderByPosicionAsc(documentoId, 0, Pageable.unpaged());
        int posicionActual = progresoUsuarioRepository.findByUsuario_IdAndDocumento_Id(usuario.getId(), documentoId)
                .map(ProgresoUsuario::getPosicionActual)
                .orElse(0);

        // La posicion "NUEVA" de una unidad es su primera aparicion en la
        // secuencia - eso es lo que cuenta como "pasada" para avance, no
        // sus reapariciones como REFUERZO (serian la misma unidad contada
        // varias veces).
        Map<UUID, Integer> posicionNuevaPorUnidad = todos.stream()
                .filter(e -> e.getTipoElemento() == TipoElemento.NUEVA)
                .collect(Collectors.toMap(e -> e.getUnidad().getId(), SecuenciaTablero::getPosicion, (a, b) -> a));

        Set<UUID> dominadas = resultadoUnidadRepository
                .findByUsuario_IdAndUnidad_Documento_Id(usuario.getId(), documentoId).stream()
                .filter(r -> r.getEstado() == EstadoResultado.DOMINADA)
                .map(r -> r.getUnidad().getId())
                .collect(Collectors.toSet());

        Map<UUID, List<Unidad>> porSeccion = jugables.stream()
                .collect(Collectors.groupingBy(u -> u.getSeccion().getId(), HashMap::new, Collectors.toList()));

        List<ProgresoSeccionDTO> secciones = porSeccion.entrySet().stream()
                .map(entrada -> new ProgresoSeccionDTO(
                        entrada.getKey(),
                        entrada.getValue().size(),
                        contarPasadas(entrada.getValue(), posicionNuevaPorUnidad, posicionActual),
                        contarDominadas(entrada.getValue(), dominadas)))
                .toList();

        double fraccionAvance = todos.isEmpty()
                ? 0
                : (double) todos.stream().filter(e -> e.getPosicion() < posicionActual).count() / todos.size();

        return new ProgresoDocumentoResponse(
                fraccionAvance, jugables.size(), contarDominadas(jugables, dominadas), secciones);
    }

    private int contarDominadas(List<Unidad> unidades, Set<UUID> dominadas) {
        return (int) unidades.stream().filter(u -> dominadas.contains(u.getId())).count();
    }

    private int contarPasadas(List<Unidad> unidades, Map<UUID, Integer> posicionNuevaPorUnidad, int posicionActual) {
        return (int) unidades.stream()
                .map(u -> posicionNuevaPorUnidad.get(u.getId()))
                .filter(posicion -> posicion != null && posicion < posicionActual)
                .count();
    }
}
