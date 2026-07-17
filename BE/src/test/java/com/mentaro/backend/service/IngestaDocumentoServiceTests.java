package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class IngestaDocumentoServiceTests {

    @Autowired
    private IngestaDocumentoService ingestaDocumentoService;
    @Autowired
    private DocumentoTextoTemporalService textoTemporalService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @MockitoBean
    private PasadaAAsyncRunner pasadaAAsyncRunner;

    private Usuario usuario() {
        return usuarioRepository.save(new Usuario("firebase-uid-ingesta-" + UUID.randomUUID(), "x@example.com"));
    }

    @Test
    void creaElDocumentoEnProcesandoConElTituloDado() {
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "capitulo1.txt", "text/plain", "En un lugar de la Mancha...".getBytes(StandardCharsets.UTF_8));

        Documento documento = ingestaDocumentoService.crear(usuario(), archivo, "El Quijote");

        assertThat(documento.getTitulo()).isEqualTo("El Quijote");
        assertThat(documento.getEstado()).isEqualTo(EstadoDocumento.PROCESANDO);
    }

    @Test
    void siNoSeDaTituloUsaElNombreDeArchivoSinExtension() {
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "el-quijote.txt", "text/plain", "texto".getBytes(StandardCharsets.UTF_8));

        Documento documento = ingestaDocumentoService.crear(usuario(), archivo, null);

        assertThat(documento.getTitulo()).isEqualTo("el-quijote");
    }

    @Test
    void guardaElTextoExtraidoComoTextoTemporal() {
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "doc.txt", "text/plain", "contenido de prueba".getBytes(StandardCharsets.UTF_8));

        Documento documento = ingestaDocumentoService.crear(usuario(), archivo, "Doc");

        assertThat(textoTemporalService.obtener(documento.getId())).isEqualTo("contenido de prueba");
    }

    @Test
    void disparaLaPasadaAEnSegundoPlanoConElTextoExtraido() {
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "doc.txt", "text/plain", "contenido de prueba".getBytes(StandardCharsets.UTF_8));

        Documento documento = ingestaDocumentoService.crear(usuario(), archivo, "Doc");

        verify(pasadaAAsyncRunner).ejecutar(eq(documento.getId()), eq("contenido de prueba"));
    }
}
