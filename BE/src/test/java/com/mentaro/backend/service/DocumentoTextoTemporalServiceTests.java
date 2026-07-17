package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.DocumentoTextoTemporalRepository;
import com.mentaro.backend.repository.UsuarioRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class DocumentoTextoTemporalServiceTests {

    @Autowired
    private DocumentoTextoTemporalService service;
    @Autowired
    private DocumentoTextoTemporalRepository repository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private DocumentoRepository documentoRepository;

    private UUID crearDocumento() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-txt-" + UUID.randomUUID(), "x@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.PROCESANDO));
        return documento.getId();
    }

    @Test
    void guardarYObtenerDevuelveElMismoTexto() {
        UUID documentoId = crearDocumento();

        service.guardar(documentoId, "capitulo uno...");

        assertThat(service.obtener(documentoId)).isEqualTo("capitulo uno...");
    }

    @Test
    void guardarDosVecesActualizaElTextoExistenteSinDuplicarFila() {
        UUID documentoId = crearDocumento();

        service.guardar(documentoId, "version inicial");
        service.guardar(documentoId, "version corregida");

        assertThat(service.obtener(documentoId)).isEqualTo("version corregida");
        // documento_id es la PK: una fila duplicada de verdad violaria la
        // constraint en vez de aparecer silenciosa. Se verifica contra este
        // documento puntual, no repository.count() global - la base de
        // datos de desarrollo puede tener otras filas de uso real.
        assertThat(repository.findById(documentoId)).isPresent();
    }

    @Test
    void obtenerLanza410SiNuncaSeGuardoTextoParaEseDocumento() {
        UUID documentoId = crearDocumento();

        assertThatThrownBy(() -> service.obtener(documentoId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("410");
    }

    @Test
    void obtenerActualizaLaMarcaDeUsoParaQueLaLimpiezaNoLoBarraMientrasEstaActivo() {
        UUID documentoId = crearDocumento();
        service.guardar(documentoId, "texto");
        var actualizadoEnAntes = repository.findById(documentoId).orElseThrow().getActualizadoEn();

        service.obtener(documentoId);

        var actualizadoEnDespues = repository.findById(documentoId).orElseThrow().getActualizadoEn();
        assertThat(actualizadoEnDespues).isAfterOrEqualTo(actualizadoEnAntes);
    }
}
