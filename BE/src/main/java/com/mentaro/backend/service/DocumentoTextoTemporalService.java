package com.mentaro.backend.service;

import com.mentaro.backend.entity.DocumentoTextoTemporal;
import com.mentaro.backend.repository.DocumentoTextoTemporalRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Guarda el texto fuente extraido de un documento, de forma transitoria
// (ver V6__documento_texto_temporal.sql y proyecto-mentaro-vision: nunca
// persistir el texto fuente indefinidamente). No se borra al llegar a
// 'listo' porque profundizar puede necesitarlo de nuevo mucho despues -
// LimpiezaTextoTemporalJob es el unico mecanismo de borrado, por
// inactividad (ver actualizadoEn/marcarUsado).
@Service
public class DocumentoTextoTemporalService {

    private final DocumentoTextoTemporalRepository repository;

    public DocumentoTextoTemporalService(DocumentoTextoTemporalRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void guardar(UUID documentoId, String texto) {
        DocumentoTextoTemporal existente = repository.findById(documentoId).orElse(null);
        if (existente != null) {
            existente.actualizar(texto);
            repository.save(existente);
        } else {
            repository.save(new DocumentoTextoTemporal(documentoId, texto));
        }
    }

    // Lanza 410 Gone (no 404: existio, expiro) si el texto ya fue barrido
    // por inactividad - el controller debe traducir esto en un flujo de
    // re-subida para el usuario, nunca un error ciego.
    @Transactional
    public String obtener(UUID documentoId) {
        DocumentoTextoTemporal registro = repository.findById(documentoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE,
                        "El texto fuente de este documento expiro por inactividad, hay que volver a subirlo"));
        registro.marcarUsado();
        return registro.getTexto();
    }
}
