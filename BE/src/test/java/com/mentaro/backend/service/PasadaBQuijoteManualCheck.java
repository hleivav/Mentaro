package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.Unidad;
import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.UnidadRepository;
import com.mentaro.backend.repository.UsuarioRepository;
import com.mentaro.backend.entity.Usuario;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

// Chequeo MANUAL, no parte de la suite normal. Encadena Pasada A + Pasada B
// reales contra DeepSeek sobre los mismos capitulos del Quijote, y escribe
// un reporte legible a un archivo UTF-8 para revisar calidad real
// (explicaciones, distractores, tasa de escalado) - eso no lo puede
// verificar ningun assert contra Postgres. Dos capas de proteccion contra
// correrlo sin querer: (1) el nombre de la clase no termina en Test/Tests;
// (2) @EnabledIfSystemProperty se salta el test aunque -Dtest lo apunte
// explicito, a menos que se pase tambien la property de abajo. Correr a
// mano con:
//   mvn test -Dtest=PasadaBQuijoteManualCheck -Ddeepseek.live=true
@SpringBootTest
@Transactional
@EnabledIfSystemProperty(named = "deepseek.live", matches = "true")
class PasadaBQuijoteManualCheck {

    @Autowired
    private PasadaAService pasadaAService;
    @Autowired
    private PasadaBService pasadaBService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private DocumentoRepository documentoRepository;
    @Autowired
    private UnidadRepository unidadRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void generaContenidoRealParaLosPrimerosTresCapitulosDelQuijote() throws IOException {
        String texto = Files.readString(Path.of("src/test/resources/quijote-caps-1-3.txt"));

        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-quijote-b", "checkb@example.com"));
        Documento documento = documentoRepository
                .save(new Documento(usuario, "Don Quijote (caps. 1-3)", EstadoDocumento.PROCESANDO));

        pasadaAService.ejecutar(documento, texto);

        List<Unidad> declarativas = unidadRepository.findByDocumento_Id(documento.getId()).stream()
                .filter(u -> u.getTipoContenido() == com.mentaro.backend.entity.TipoContenido.DECLARATIVO)
                .toList();

        // Captura los logs de escalado de PasadaBService (cuantas veces tuvo
        // que reintentar con el modelo caro) para meterlos en el reporte.
        Logger logger = (Logger) LoggerFactory.getLogger(PasadaBService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            pasadaBService.ejecutar(documento, declarativas, texto);
        } finally {
            logger.detachAppender(appender);
        }
        List<String> logsDeEscalado = appender.list.stream()
                .map(e -> "[" + e.getLevel() + "] " + e.getFormattedMessage())
                .toList();

        StringBuilder reporte = new StringBuilder();
        reporte.append("Total unidades declarativas: ").append(declarativas.size()).append("\n\n");
        reporte.append("=== LOGS DE ESCALADO ===\n");
        logsDeEscalado.forEach(l -> reporte.append(l).append("\n"));
        reporte.append("\n");

        int generadas = 0;
        int fallidaPersistida = 0;
        int fallidaExcluida = 0;

        for (Unidad u : unidadRepository.findByDocumento_Id(documento.getId())) {
            if (u.getTipoContenido() != com.mentaro.backend.entity.TipoContenido.DECLARATIVO) {
                continue;
            }
            reporte.append("=== ").append(u.getTitulo()).append(" ===\n");
            reporte.append("nivel_importancia: ").append(u.getNivelImportancia())
                    .append(" | estado_generacion: ").append(u.getEstadoGeneracion()).append("\n");

            switch (u.getEstadoGeneracion()) {
                case GENERADA -> generadas++;
                case FALLIDA_PERSISTIDA -> fallidaPersistida++;
                case FALLIDA_EXCLUIDA -> fallidaExcluida++;
                default -> { }
            }

            if (u.tieneContenido()) {
                reporte.append("explicacion_corta: ").append(u.getExplicacionCorta()).append("\n");
                reporte.append("explicacion_alternativa: ").append(u.getExplicacionAlternativa()).append("\n");
                reporte.append("pregunta_reconocimiento: ")
                        .append(formatearPregunta(u.getPreguntaReconocimiento())).append("\n");
                reporte.append("pregunta_refuerzo: ")
                        .append(formatearPregunta(u.getPreguntaRefuerzo())).append("\n");
            } else {
                reporte.append("(sin contenido - excluida del juego)\n");
            }
            reporte.append("\n");
        }

        reporte.append("=== RESUMEN ===\n");
        reporte.append("generada: ").append(generadas).append("\n");
        reporte.append("fallida_persistida (esencial, revisar): ").append(fallidaPersistida).append("\n");
        reporte.append("fallida_excluida (importante/detalle, fuera del juego): ").append(fallidaExcluida).append("\n");

        Path salida = Path.of("target/pasada-b-quijote-review.txt");
        Files.writeString(salida, reporte.toString(), java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("Reporte escrito en: " + salida.toAbsolutePath());

        assertThat(declarativas).isNotEmpty();
        assertThat(generadas + fallidaPersistida + fallidaExcluida).isEqualTo(declarativas.size());
    }

    // Formateo generico por Map, no un record de esquema fijo - la pregunta
    // puede ser opcion_multiple, ordenar o emparejar (ver PasadaBService),
    // y este reporte es solo para revision humana, no para un assert.
    @SuppressWarnings("unchecked")
    private String formatearPregunta(String json) {
        if (json == null) {
            return "(null)";
        }
        try {
            var pregunta = (java.util.Map<String, Object>) objectMapper.readValue(json, java.util.Map.class);
            return "\n    " + pregunta;
        } catch (Exception e) {
            return "(no se pudo parsear: " + json + ")";
        }
    }
}
