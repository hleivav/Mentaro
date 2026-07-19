package com.mentaro.backend.service;

import com.mentaro.backend.repository.DocumentoImagenTemporalRepository;
import com.mentaro.backend.repository.DocumentoTextoTemporalRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Unico mecanismo de borrado de documento_texto_temporal y
// documento_imagen_temporal: barren por inactividad (48h desde la ultima
// escritura o lectura, ver marcarUsado en ambos servicios), sin importar
// el estado del documento - profundizar puede seguir usando el texto
// indefinidamente mientras el usuario vuelva antes de esa ventana. Las
// imagenes comparten la misma ventana y cadencia a proposito (mismo
// espiritu, "vive mientras el texto fuente sigue vivo"), aunque cada tabla
// trackea su propio actualizado_en de forma independiente.
@Component
public class LimpiezaTextoTemporalJob {

    private static final Logger log = LoggerFactory.getLogger(LimpiezaTextoTemporalJob.class);
    private static final Duration VENTANA_INACTIVIDAD = Duration.ofHours(48);

    private final DocumentoTextoTemporalRepository textoRepository;
    private final DocumentoImagenTemporalRepository imagenRepository;

    public LimpiezaTextoTemporalJob(
            DocumentoTextoTemporalRepository textoRepository, DocumentoImagenTemporalRepository imagenRepository) {
        this.textoRepository = textoRepository;
        this.imagenRepository = imagenRepository;
    }

    @Scheduled(fixedRateString = "PT1H")
    @Transactional
    public void limpiar() {
        Instant limite = Instant.now().minus(VENTANA_INACTIVIDAD);
        int textosBorrados = textoRepository.deleteByActualizadoEnBefore(limite);
        if (textosBorrados > 0) {
            log.info("Limpieza de texto temporal: {} documento(s) barridos por inactividad (>{}h)",
                    textosBorrados, VENTANA_INACTIVIDAD.toHours());
        }

        int imagenesBorradas = imagenRepository.deleteByActualizadoEnBefore(limite);
        if (imagenesBorradas > 0) {
            log.info("Limpieza de imagenes temporales: {} imagen(es) barridas por inactividad (>{}h)",
                    imagenesBorradas, VENTANA_INACTIVIDAD.toHours());
        }
    }
}
