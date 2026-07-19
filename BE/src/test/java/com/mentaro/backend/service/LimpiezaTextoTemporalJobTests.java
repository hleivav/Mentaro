package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.DocumentoImagenTemporalRepository;
import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.DocumentoTextoTemporalRepository;
import com.mentaro.backend.repository.UsuarioRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class LimpiezaTextoTemporalJobTests {

    @Autowired
    private LimpiezaTextoTemporalJob job;
    @Autowired
    private DocumentoTextoTemporalService service;
    @Autowired
    private DocumentoTextoTemporalRepository repository;
    @Autowired
    private DocumentoImagenTemporalService imagenService;
    @Autowired
    private DocumentoImagenTemporalRepository imagenRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private DocumentoRepository documentoRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID crearDocumentoConTextoDeAntiguedad(long horasDeAntiguedad) {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-limpieza-" + UUID.randomUUID(), "x@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.PROCESANDO));
        service.guardar(documento.getId(), "texto");
        repository.flush();
        jdbcTemplate.update("UPDATE documento_texto_temporal SET actualizado_en = ? WHERE documento_id = ?",
                Timestamp.from(Instant.now().minus(horasDeAntiguedad, ChronoUnit.HOURS)), documento.getId());
        return documento.getId();
    }

    @Test
    void limpiarBorraTextoInactivoPorMasDe48Horas() {
        UUID documentoId = crearDocumentoConTextoDeAntiguedad(49);

        job.limpiar();

        assertThat(repository.findById(documentoId)).isEmpty();
    }

    @Test
    void limpiarNoBorraTextoUsadoRecientemente() {
        UUID documentoId = crearDocumentoConTextoDeAntiguedad(1);

        job.limpiar();

        assertThat(repository.findById(documentoId)).isPresent();
    }

    @Test
    void limpiarBorraImagenesInactivasPorMasDe48HorasPeroNoLasRecientes() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-limpieza-img-" + UUID.randomUUID(), "x@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.PROCESANDO));
        imagenService.guardar(documento.getId(),
                List.of(new DescriptorImagenesPdf.ImagenDescrita(0, "desc", new byte[] {1})));
        UUID imagenId = imagenService.listar(documento.getId()).getFirst().getId();
        imagenRepository.flush();
        jdbcTemplate.update("UPDATE documento_imagen_temporal SET actualizado_en = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(49, ChronoUnit.HOURS)), imagenId);

        job.limpiar();

        assertThat(imagenRepository.findById(imagenId)).isEmpty();
    }
}
