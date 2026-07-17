package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.NivelImportancia;
import com.mentaro.backend.entity.Seccion;
import com.mentaro.backend.entity.TipoContenido;
import com.mentaro.backend.entity.Unidad;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.SeccionRepository;
import com.mentaro.backend.repository.UnidadRepository;
import com.mentaro.backend.repository.UsuarioRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Sin @Transactional, misma razon que PasadaAAsyncRunnerTests: el runner
// corre en otro hilo real via el proxy @Async, y ese hilo no ve datos de
// una transaccion de test sin confirmar.
@SpringBootTest
class PasadaBAsyncRunnerTests {

    @Autowired
    private PasadaBAsyncRunner runner;
    @MockitoBean
    private PasadaBService pasadaBService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private DocumentoRepository documentoRepository;
    @Autowired
    private SeccionRepository seccionRepository;
    @Autowired
    private UnidadRepository unidadRepository;

    private UUID documentoId;

    @AfterEach
    void limpiar() {
        if (documentoId != null) {
            documentoRepository.findById(documentoId).ifPresent(documento -> {
                unidadRepository.deleteAll(unidadRepository.findByDocumento_Id(documentoId));
                seccionRepository.findByDocumento_Id(documentoId).forEach(seccionRepository::delete);
                documentoRepository.delete(documento);
                usuarioRepository.delete(documento.getUsuario());
            });
        }
    }

    private Documento crearDocumentoConUnidad(EstadoDocumento estado) {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-bruna-" + UUID.randomUUID(), "x@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", estado));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        unidadRepository.save(new Unidad(documento, seccion, "Titulo", TipoContenido.DECLARATIVO, NivelImportancia.ESENCIAL));
        documentoId = documento.getId();
        return documento;
    }

    @Test
    void siFallaYEraPrimeraGeneracionVuelveAMapeado() throws InterruptedException {
        crearDocumentoConUnidad(EstadoDocumento.GENERANDO);
        doThrow(new RuntimeException("DeepSeek no respondio")).when(pasadaBService).ejecutar(any(), any(), any());
        List<UUID> idsSeleccionados = unidadRepository.findByDocumento_Id(documentoId).stream().map(Unidad::getId).toList();

        runner.ejecutar(documentoId, idsSeleccionados, "texto");

        verify(pasadaBService, timeout(5000)).ejecutar(any(), any(), any());
        EstadoDocumento estadoFinal = esperarEstadoDistintoDe(EstadoDocumento.GENERANDO);
        assertThat(estadoFinal).isEqualTo(EstadoDocumento.MAPEADO);
    }

    @Test
    void siFallaYEraProfundizarDejaListoIntacto() throws InterruptedException {
        crearDocumentoConUnidad(EstadoDocumento.LISTO);
        doThrow(new RuntimeException("DeepSeek no respondio")).when(pasadaBService).ejecutar(any(), any(), any());
        List<UUID> idsSeleccionados = unidadRepository.findByDocumento_Id(documentoId).stream().map(Unidad::getId).toList();

        runner.ejecutar(documentoId, idsSeleccionados, "texto");

        verify(pasadaBService, timeout(5000)).ejecutar(any(), any(), any());
        Thread.sleep(200);
        assertThat(documentoRepository.findById(documentoId).orElseThrow().getEstado()).isEqualTo(EstadoDocumento.LISTO);
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
