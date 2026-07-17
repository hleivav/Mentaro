package com.mentaro.backend.service;

import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.DocumentoRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// Lectura de documentos (para el polling de estado y, mas adelante, la
// pantalla de seleccion). Sin restriccion de estado a proposito - a
// diferencia de SesionService.cargarDocumentoDe, este endpoint es el que
// el frontend consulta MIENTRAS el documento todavia esta procesando.
@Service
public class DocumentoConsultaService {

    private final DocumentoRepository documentoRepository;

    public DocumentoConsultaService(DocumentoRepository documentoRepository) {
        this.documentoRepository = documentoRepository;
    }

    public Documento obtener(Usuario usuario, UUID documentoId) {
        Documento documento = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento no encontrado"));
        if (!documento.getUsuario().getId().equals(usuario.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El documento no pertenece al usuario");
        }
        return documento;
    }

    public List<Documento> listar(Usuario usuario) {
        return documentoRepository.findByUsuario_IdOrderByCreadoEnDesc(usuario.getId());
    }
}
