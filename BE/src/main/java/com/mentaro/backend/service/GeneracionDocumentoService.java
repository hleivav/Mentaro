package com.mentaro.backend.service;

import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.DocumentoRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// Orquesta POST /documentos/{id}/generar: valida dueno y estado, obtiene el
// texto fuente (si expiro por inactividad, DocumentoTextoTemporalService ya
// lanza 410 con un mensaje claro - ESE es el cierre del contrato, no un
// reintento silencioso de la Pasada A, que ademas ya no seria posible sin
// el archivo original que nunca se guarda por diseno de copyright), fija
// GENERANDO de forma sincrona si es la primera generacion (para que el
// polling lo vea desde ya, ver PasadaBService) y recien ahi dispara la
// Pasada B en segundo plano.
@Service
public class GeneracionDocumentoService {

    private final DocumentoConsultaService documentoConsultaService;
    private final DocumentoTextoTemporalService textoTemporalService;
    private final DocumentoRepository documentoRepository;
    private final PasadaBAsyncRunner pasadaBAsyncRunner;

    public GeneracionDocumentoService(
            DocumentoConsultaService documentoConsultaService,
            DocumentoTextoTemporalService textoTemporalService,
            DocumentoRepository documentoRepository,
            PasadaBAsyncRunner pasadaBAsyncRunner) {
        this.documentoConsultaService = documentoConsultaService;
        this.textoTemporalService = textoTemporalService;
        this.documentoRepository = documentoRepository;
        this.pasadaBAsyncRunner = pasadaBAsyncRunner;
    }

    public Documento generar(Usuario usuario, UUID documentoId, List<UUID> unidadIdsSeleccionadas) {
        Documento documento = documentoConsultaService.obtener(usuario, documentoId);
        if (documento.getEstado() != EstadoDocumento.MAPEADO && documento.getEstado() != EstadoDocumento.LISTO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El documento debe estar mapeado o listo para generar contenido (actual: "
                            + documento.getEstado() + ")");
        }

        String textoFuente = textoTemporalService.obtener(documentoId);

        if (documento.getEstado() == EstadoDocumento.MAPEADO) {
            documento.setEstado(EstadoDocumento.GENERANDO);
            documentoRepository.save(documento);
        }

        pasadaBAsyncRunner.ejecutar(documento.getId(), unidadIdsSeleccionadas, textoFuente);
        return documento;
    }
}
