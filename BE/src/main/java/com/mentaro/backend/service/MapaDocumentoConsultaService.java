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
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

        Map<UUID, List<UnidadMapaDTO>> unidadesPorSeccion = new HashMap<>();
        for (Unidad unidad : unidadRepository.findByDocumento_Id(documentoId)) {
            if (unidad.getTipoContenido() != TipoContenido.DECLARATIVO) {
                continue;
            }
            unidadesPorSeccion
                    .computeIfAbsent(unidad.getSeccion().getId(), k -> new ArrayList<>())
                    .add(new UnidadMapaDTO(unidad.getId(), unidad.getNivelImportancia().name().toLowerCase(Locale.ROOT)));
        }

        return new MapaDocumentoResponse(
                seccionRepository.findByDocumento_Id(documentoId).stream()
                        .map(seccion -> aDto(seccion, unidadesPorSeccion))
                        .toList());
    }

    private SeccionMapaDTO aDto(Seccion seccion, Map<UUID, List<UnidadMapaDTO>> unidadesPorSeccion) {
        return new SeccionMapaDTO(
                seccion.getId(),
                seccion.getTitulo(),
                seccion.getPadre() != null ? seccion.getPadre().getId() : null,
                seccion.getResumen(),
                unidadesPorSeccion.getOrDefault(seccion.getId(), List.of()));
    }
}
