package com.mentaro.backend.service;

import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.repository.DocumentoRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// Bean separado (no un metodo mas de IngestaDocumentoService) a proposito:
// @Async solo funciona a traves del proxy de Spring, y una auto-invocacion
// (this.metodo() desde otro metodo de la misma clase) lo esquiva
// silenciosamente - misma trampa que ya nos mordio con el bean de
// FirebaseAuth. Al vivir en su propio bean, la llamada desde
// IngestaDocumentoService siempre pasa por el proxy.
@Component
public class PasadaAAsyncRunner {

    private static final Logger log = LoggerFactory.getLogger(PasadaAAsyncRunner.class);

    private final DocumentoRepository documentoRepository;
    private final PasadaAService pasadaAService;

    public PasadaAAsyncRunner(DocumentoRepository documentoRepository, PasadaAService pasadaAService) {
        this.documentoRepository = documentoRepository;
        this.pasadaAService = pasadaAService;
    }

    @Async
    public void ejecutar(UUID documentoId, String textoFuente) {
        try {
            Documento documento = documentoRepository.findById(documentoId)
                    .orElseThrow(() -> new IllegalStateException("Documento no encontrado: " + documentoId));
            pasadaAService.ejecutar(documento, textoFuente);
        } catch (Exception e) {
            log.error("Pasada A fallo para documento {}: {}", documentoId, e.getMessage(), e);
            marcarError(documentoId);
        }
    }

    // findById y save son cada uno transaccional por si solos (llamadas al
    // bean del repositorio, no auto-invocacion) - no hace falta envolver
    // esto en una transaccion propia.
    private void marcarError(UUID documentoId) {
        documentoRepository.findById(documentoId).ifPresent(documento -> {
            documento.setEstado(EstadoDocumento.ERROR);
            documentoRepository.save(documento);
        });
    }
}
