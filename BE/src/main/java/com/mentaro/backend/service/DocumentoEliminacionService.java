package com.mentaro.backend.service;

import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.ProgresoUsuarioRepository;
import com.mentaro.backend.repository.ResultadoUnidadRepository;
import com.mentaro.backend.repository.SeccionRepository;
import com.mentaro.backend.repository.SecuenciaTableroRepository;
import com.mentaro.backend.repository.UnidadRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Borra un documento y TODO lo que depende de el (secciones, unidades,
// secuencia_tablero, progreso_usuario, resultado_unidad). Ninguna de esas
// tablas tiene ON DELETE CASCADE hacia documentos (solo
// documento_texto_temporal la tiene) - hay que borrar a mano, en el
// orden correcto (hijos antes que padres), o la FK rechaza el borrado
// del documento.
@Service
public class DocumentoEliminacionService {

    private final DocumentoConsultaService documentoConsultaService;
    private final DocumentoRepository documentoRepository;
    private final ResultadoUnidadRepository resultadoUnidadRepository;
    private final SecuenciaTableroRepository secuenciaTableroRepository;
    private final ProgresoUsuarioRepository progresoUsuarioRepository;
    private final UnidadRepository unidadRepository;
    private final SeccionRepository seccionRepository;

    public DocumentoEliminacionService(
            DocumentoConsultaService documentoConsultaService,
            DocumentoRepository documentoRepository,
            ResultadoUnidadRepository resultadoUnidadRepository,
            SecuenciaTableroRepository secuenciaTableroRepository,
            ProgresoUsuarioRepository progresoUsuarioRepository,
            UnidadRepository unidadRepository,
            SeccionRepository seccionRepository) {
        this.documentoConsultaService = documentoConsultaService;
        this.documentoRepository = documentoRepository;
        this.resultadoUnidadRepository = resultadoUnidadRepository;
        this.secuenciaTableroRepository = secuenciaTableroRepository;
        this.progresoUsuarioRepository = progresoUsuarioRepository;
        this.unidadRepository = unidadRepository;
        this.seccionRepository = seccionRepository;
    }

    @Transactional
    public void eliminar(Usuario usuario, UUID documentoId) {
        Documento documento = documentoConsultaService.obtener(usuario, documentoId);

        resultadoUnidadRepository.deleteByUnidad_Documento_Id(documentoId);
        secuenciaTableroRepository.deleteByDocumento_Id(documentoId);
        progresoUsuarioRepository.deleteByDocumento_Id(documentoId);
        unidadRepository.deleteByDocumento_Id(documentoId);
        seccionRepository.deleteByDocumento_Id(documentoId);
        // documento_texto_temporal tiene ON DELETE CASCADE - no hace falta
        // borrarla a mano.
        documentoRepository.delete(documento);
    }
}
