package com.mentaro.backend.service;

import com.mentaro.backend.repository.DocumentoTextoTemporalRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Unico mecanismo de borrado de documento_texto_temporal: barre por
// inactividad (48h desde la ultima escritura o lectura, ver
// DocumentoTextoTemporalService.marcarUsado), sin importar el estado del
// documento - profundizar puede seguir usando el texto indefinidamente
// mientras el usuario vuelva antes de esa ventana.
@Component
public class LimpiezaTextoTemporalJob {

    private static final Logger log = LoggerFactory.getLogger(LimpiezaTextoTemporalJob.class);
    private static final Duration VENTANA_INACTIVIDAD = Duration.ofHours(48);

    private final DocumentoTextoTemporalRepository repository;

    public LimpiezaTextoTemporalJob(DocumentoTextoTemporalRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedRateString = "PT1H")
    @Transactional
    public void limpiar() {
        int borrados = repository.deleteByActualizadoEnBefore(Instant.now().minus(VENTANA_INACTIVIDAD));
        if (borrados > 0) {
            log.info("Limpieza de texto temporal: {} documento(s) barridos por inactividad (>{}h)",
                    borrados, VENTANA_INACTIVIDAD.toHours());
        }
    }
}
