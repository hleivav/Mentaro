package com.mentaro.backend.service;

import com.mentaro.backend.dto.ImagenDocumentoDTO;
import com.mentaro.backend.entity.DocumentoImagenTemporal;
import com.mentaro.backend.entity.Usuario;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

// Capa de consulta con verificacion de dueño para las imagenes temporales -
// DocumentoImagenTemporalService no conoce usuarios, solo documento_id.
@Service
public class ImagenDocumentoConsultaService {

    private final DocumentoConsultaService documentoConsultaService;
    private final DocumentoImagenTemporalService imagenTemporalService;

    public ImagenDocumentoConsultaService(
            DocumentoConsultaService documentoConsultaService, DocumentoImagenTemporalService imagenTemporalService) {
        this.documentoConsultaService = documentoConsultaService;
        this.imagenTemporalService = imagenTemporalService;
    }

    public List<ImagenDocumentoDTO> listar(Usuario usuario, UUID documentoId) {
        documentoConsultaService.obtener(usuario, documentoId);
        return imagenTemporalService.listar(documentoId).stream()
                .map(imagen -> new ImagenDocumentoDTO(imagen.getId(), imagen.getPagina(), imagen.getDescripcion()))
                .toList();
    }

    public DocumentoImagenTemporal obtener(Usuario usuario, UUID documentoId, UUID imagenId) {
        documentoConsultaService.obtener(usuario, documentoId);
        return imagenTemporalService.obtener(documentoId, imagenId);
    }
}
