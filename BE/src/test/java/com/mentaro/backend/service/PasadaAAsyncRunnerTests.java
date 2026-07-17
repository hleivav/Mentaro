package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.UsuarioRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Sin @Transactional: el runner corre de verdad en otro hilo (@Async via
// proxy real de Spring) sobre otra conexion, asi que si el test envolviera
// todo en una transaccion sin confirmar, ese hilo nunca veria el documento
// recien creado (READ_COMMITTED). Los guardados se confirman de una, y se
// limpian a mano al final.
@SpringBootTest
class PasadaAAsyncRunnerTests {

    @Autowired
    private PasadaAAsyncRunner runner;
    @MockitoBean
    private PasadaAService pasadaAService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private DocumentoRepository documentoRepository;

    private UUID documentoId;

    @AfterEach
    void limpiar() {
        if (documentoId != null) {
            documentoRepository.findById(documentoId).ifPresent(documento -> {
                documentoRepository.delete(documento);
                usuarioRepository.delete(documento.getUsuario());
            });
        }
    }

    @Test
    void siPasadaAFallaElDocumentoQuedaEnError() throws InterruptedException {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-async-" + UUID.randomUUID(), "x@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.PROCESANDO));
        documentoId = documento.getId();
        doThrow(new RuntimeException("DeepSeek no respondio")).when(pasadaAService).ejecutar(any(), any());

        runner.ejecutar(documentoId, "texto fuente");

        verify(pasadaAService, timeout(5000)).ejecutar(any(), any());
        EstadoDocumento estadoFinal = esperarEstadoDistintoDe(EstadoDocumento.PROCESANDO);
        assertThat(estadoFinal).isEqualTo(EstadoDocumento.ERROR);
    }

    private EstadoDocumento esperarEstadoDistintoDe(EstadoDocumento estadoInicial) throws InterruptedException {
        long limite = System.currentTimeMillis() + 5000;
        EstadoDocumento actual;
        do {
            actual = documentoRepository.findById(documentoId).orElseThrow().getEstado();
            if (actual != estadoInicial) {
                return actual;
            }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < limite);
        return actual;
    }
}
