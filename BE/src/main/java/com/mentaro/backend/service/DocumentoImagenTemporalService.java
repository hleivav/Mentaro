package com.mentaro.backend.service;

import com.mentaro.backend.entity.DocumentoImagenTemporal;
import com.mentaro.backend.repository.DocumentoImagenTemporalRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Guarda las imagenes embebidas extraidas de un PDF, de forma transitoria
// (mismo espiritu que DocumentoTextoTemporalService: nunca persistir
// indefinidamente, ver LimpiezaTextoTemporalJob). La imagen original SI se
// le muestra al usuario mientras juega - la version anterior del diseño
// las descartaba por una cautela de copyright que resulto excesiva.
@Service
public class DocumentoImagenTemporalService {

    private static final String MEDIA_TYPE_PNG = "image/png";

    private final DocumentoImagenTemporalRepository repository;

    public DocumentoImagenTemporalService(DocumentoImagenTemporalRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void guardar(UUID documentoId, List<DescriptorImagenesPdf.ImagenDescrita> imagenes) {
        int orden = 0;
        for (DescriptorImagenesPdf.ImagenDescrita imagen : imagenes) {
            repository.save(new DocumentoImagenTemporal(
                    imagen.id(), documentoId, imagen.pagina(), orden++, imagen.descripcion(), imagen.pngBytes(),
                    MEDIA_TYPE_PNG, imagen.esEsencial()));
        }
    }

    @Transactional
    public List<DocumentoImagenTemporal> listar(UUID documentoId) {
        return repository.findByDocumentoIdOrderByPaginaAscOrdenAsc(documentoId);
    }

    // 404, no 410 como el texto fuente: a diferencia de ese caso, no hay un
    // flujo de recuperacion obligatorio si esto ya expiro - es contenido
    // opcional de "ver imagenes", nunca algo que bloquee seguir jugando.
    @Transactional
    public DocumentoImagenTemporal obtener(UUID documentoId, UUID imagenId) {
        DocumentoImagenTemporal imagen = repository.findById(imagenId)
                .filter(i -> i.getDocumentoId().equals(documentoId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagen no encontrada"));
        imagen.marcarUsado();
        return imagen;
    }
}
