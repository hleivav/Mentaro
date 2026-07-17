package com.mentaro.backend.service;

import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.Unidad;
import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.UnidadRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// Bean separado por la misma razon que PasadaAAsyncRunner: @Async solo
// aplica a traves del proxy de Spring, una auto-invocacion lo esquiva.
//
// La transicion a GENERANDO (si correspondia) ya paso ANTES de encolar
// esto - ver GeneracionDocumentoService - asi que ese estado ya es visible
// para el polling desde antes de que este metodo arranque a correr.
@Component
public class PasadaBAsyncRunner {

    private static final Logger log = LoggerFactory.getLogger(PasadaBAsyncRunner.class);

    private final DocumentoRepository documentoRepository;
    private final UnidadRepository unidadRepository;
    private final PasadaBService pasadaBService;

    public PasadaBAsyncRunner(
            DocumentoRepository documentoRepository, UnidadRepository unidadRepository, PasadaBService pasadaBService) {
        this.documentoRepository = documentoRepository;
        this.unidadRepository = unidadRepository;
        this.pasadaBService = pasadaBService;
    }

    @Async
    public void ejecutar(UUID documentoId, List<UUID> unidadIdsSeleccionadas, String textoFuente) {
        try {
            Documento documento = documentoRepository.findById(documentoId)
                    .orElseThrow(() -> new IllegalStateException("Documento no encontrado: " + documentoId));
            List<Unidad> unidadesSeleccionadas = seleccionar(documentoId, unidadIdsSeleccionadas);
            pasadaBService.ejecutar(documento, unidadesSeleccionadas, textoFuente);
        } catch (Exception e) {
            log.error("Pasada B fallo para documento {}: {}", documentoId, e.getMessage(), e);
            recuperarDeFallo(documentoId);
        }
    }

    private List<Unidad> seleccionar(UUID documentoId, List<UUID> unidadIdsSeleccionadas) {
        Set<UUID> idsSeleccionados = new HashSet<>(unidadIdsSeleccionadas);
        return unidadRepository.findByDocumento_Id(documentoId).stream()
                .filter(u -> idsSeleccionados.contains(u.getId()))
                .toList();
    }

    // GENERANDO (primera generacion en curso) -> vuelve a MAPEADO: el
    // esqueleto sigue intacto, el usuario puede reintentar sin volver a
    // subir nada. LISTO (profundizar) -> se deja tal cual: el contenido ya
    // generado antes sigue jugable, el intento fallido de profundizar no
    // se nota mas que en el log.
    private void recuperarDeFallo(UUID documentoId) {
        documentoRepository.findById(documentoId).ifPresent(documento -> {
            if (documento.getEstado() == EstadoDocumento.GENERANDO) {
                documento.setEstado(EstadoDocumento.MAPEADO);
                documentoRepository.save(documento);
            }
        });
    }
}
