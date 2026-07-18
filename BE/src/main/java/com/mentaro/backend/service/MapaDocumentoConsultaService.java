package com.mentaro.backend.service;

import com.mentaro.backend.dto.MapaDocumentoResponse;
import com.mentaro.backend.dto.SeccionMapaDTO;
import com.mentaro.backend.dto.UnidadMapaDTO;
import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.Seccion;
import com.mentaro.backend.entity.TipoContenido;
import com.mentaro.backend.entity.Unidad;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.SeccionRepository;
import com.mentaro.backend.repository.UnidadRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// Para la pantalla de seleccion de secciones: arbol + la lista de unidades
// DIRECTAS de cada seccion (id + nivel_importancia, nada de contenido -
// todavia no existe en este punto). Solo unidades DECLARATIVO - son las
// unicas que la Pasada B llega a generar (ver PasadaBService), asi que son
// las unicas relevantes tanto para estimar tiempo de juego como para
// construir la seleccion real. El frontend deriva los conteos por nivel
// agrupando esta lista (y suma sobre el subarbol si el usuario selecciona
// una seccion padre) - se evita duplicar esa cuenta en el backend cuando
// el cliente de todos modos necesita los ids sueltos para el POST
// /generar posterior.
@Service
public class MapaDocumentoConsultaService {

    private final DocumentoConsultaService documentoConsultaService;
    private final SeccionRepository seccionRepository;
    private final UnidadRepository unidadRepository;

    public MapaDocumentoConsultaService(
            DocumentoConsultaService documentoConsultaService,
            SeccionRepository seccionRepository,
            UnidadRepository unidadRepository) {
        this.documentoConsultaService = documentoConsultaService;
        this.seccionRepository = seccionRepository;
        this.unidadRepository = unidadRepository;
    }

    public MapaDocumentoResponse obtenerMapa(Usuario usuario, UUID documentoId) {
        Documento documento = documentoConsultaService.obtener(usuario, documentoId);
        if (documento.getEstado() == EstadoDocumento.PROCESANDO || documento.getEstado() == EstadoDocumento.ERROR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El documento todavia no tiene una estructura mapeada (estado actual: "
                            + documento.getEstado() + ")");
        }

        List<Unidad> unidades = unidadRepository.findByDocumento_Id(documentoId);

        // Necesario para resolver a que seccion pertenece cada id referenciado en
        // depende_de - incluye unidades no declarativas porque una dependencia
        // podria apuntar a cualquier tipo.
        Map<UUID, UUID> seccionPorUnidad = new HashMap<>();
        for (Unidad unidad : unidades) {
            seccionPorUnidad.put(unidad.getId(), unidad.getSeccion().getId());
        }

        Map<UUID, List<UnidadMapaDTO>> unidadesPorSeccion = new HashMap<>();
        Map<UUID, Set<UUID>> dependeDePorSeccion = new HashMap<>();
        for (Unidad unidad : unidades) {
            if (unidad.getTipoContenido() != TipoContenido.DECLARATIVO) {
                continue;
            }
            UUID seccionId = unidad.getSeccion().getId();
            unidadesPorSeccion
                    .computeIfAbsent(seccionId, k -> new ArrayList<>())
                    .add(new UnidadMapaDTO(unidad.getId(), unidad.getNivelImportancia().name().toLowerCase(Locale.ROOT)));

            for (UUID dependenciaUnidadId : unidad.getDependeDe()) {
                UUID seccionDependida = seccionPorUnidad.get(dependenciaUnidadId);
                if (seccionDependida != null && !seccionDependida.equals(seccionId)) {
                    dependeDePorSeccion.computeIfAbsent(seccionId, k -> new LinkedHashSet<>()).add(seccionDependida);
                }
            }
        }

        return new MapaDocumentoResponse(
                seccionRepository.findByDocumento_Id(documentoId).stream()
                        .map(seccion -> aDto(seccion, unidadesPorSeccion, dependeDePorSeccion))
                        .toList());
    }

    private SeccionMapaDTO aDto(
            Seccion seccion,
            Map<UUID, List<UnidadMapaDTO>> unidadesPorSeccion,
            Map<UUID, Set<UUID>> dependeDePorSeccion) {
        return new SeccionMapaDTO(
                seccion.getId(),
                seccion.getTitulo(),
                seccion.getPadre() != null ? seccion.getPadre().getId() : null,
                seccion.getResumen(),
                unidadesPorSeccion.getOrDefault(seccion.getId(), List.of()),
                List.copyOf(dependeDePorSeccion.getOrDefault(seccion.getId(), Set.of())));
    }
}
