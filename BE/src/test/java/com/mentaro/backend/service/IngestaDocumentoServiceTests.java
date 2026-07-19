package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mentaro.backend.anthropic.AnthropicClient;
import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.DocumentoImagenTemporal;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.UsuarioRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
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
    private DocumentoImagenTemporalService imagenTemporalService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @MockitoBean
    private PasadaAAsyncRunner pasadaAAsyncRunner;
    @MockitoBean
    private AnthropicClient anthropicClient;

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

    @Test
    void guardaLasImagenesDescritasComoImagenTemporal() throws IOException {
        when(anthropicClient.describirImagen(any(), anyString(), anyString())).thenReturn("Un molino de viento.");
        MockMultipartFile archivo = new MockMultipartFile("archivo", "manual.pdf", "application/pdf", pdfConImagen(200, 200));

        Documento documento = ingestaDocumentoService.crear(usuario(), archivo, "Doc");

        List<DocumentoImagenTemporal> imagenes = imagenTemporalService.listar(documento.getId());
        assertThat(imagenes).singleElement().satisfies(imagen -> {
            assertThat(imagen.getDescripcion()).isEqualTo("Un molino de viento.");
            assertThat(imagen.getImagenBytes()).isNotEmpty();
            assertThat(imagen.getMediaType()).isEqualTo("image/png");
        });
    }

    private byte[] pdfConImagen(int ancho, int alto) throws IOException {
        try (PDDocument pdf = new PDDocument()) {
            PDPage pagina = new PDPage();
            pdf.addPage(pagina);
            BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
            PDImageXObject imagenXObject = LosslessFactory.createFromImage(pdf, imagen);
            try (PDPageContentStream contenido = new PDPageContentStream(pdf, pagina)) {
                contenido.drawImage(imagenXObject, 50, 400, ancho, alto);
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            pdf.save(salida);
            return salida.toByteArray();
        }
    }
}
