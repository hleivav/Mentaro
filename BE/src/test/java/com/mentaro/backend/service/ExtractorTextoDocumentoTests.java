package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mentaro.backend.anthropic.AnthropicClient;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class ExtractorTextoDocumentoTests {

    private final AnthropicClient anthropicClient = mock(AnthropicClient.class);
    private final ExtractorTextoDocumento extractor =
            new ExtractorTextoDocumento(new DescriptorImagenesPdf(anthropicClient));

    @Test
    void extraeTextoPlanoDeUnArchivoTxt() {
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "capitulo1.txt", "text/plain", "En un lugar de la Mancha...".getBytes(StandardCharsets.UTF_8));

        ExtractorTextoDocumento.ResultadoExtraccion resultado = extractor.extraer(archivo);

        assertThat(resultado.texto()).isEqualTo("En un lugar de la Mancha...");
        assertThat(resultado.imagenes()).isEmpty();
    }

    @Test
    void extraeTextoDeUnPdfConCapaDeTexto() throws IOException {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "manual.pdf", "application/pdf", pdfDePrueba());

        assertThat(extractor.extraer(archivo).texto()).contains("En un lugar de la Mancha");
    }

    @Test
    void describeImagenesGrandesInsertaLaDescripcionEnElTextoYDevuelveLaImagen() throws IOException {
        when(anthropicClient.describirImagen(any(), anyString(), anyString()))
                .thenReturn("Un molino de viento junto a un camino.");
        MockMultipartFile archivo =
                new MockMultipartFile("archivo", "manual.pdf", "application/pdf", pdfConImagen(200, 200));

        ExtractorTextoDocumento.ResultadoExtraccion resultado = extractor.extraer(archivo);

        assertThat(resultado.texto()).contains("En un lugar de la Mancha");
        assertThat(resultado.imagenes()).singleElement().satisfies(imagen -> {
            assertThat(imagen.descripcion()).isEqualTo("Un molino de viento junto a un camino.");
            assertThat(imagen.pngBytes()).isNotEmpty();
            // El marcador insertado en el texto lleva el mismo uuid que la
            // imagen persistida - eso es lo que permite despues asociarla a
            // una unidad especifica (ver PasadaAService).
            assertThat(resultado.texto()).contains(
                    "[Descripción de imagen #" + imagen.id() + ": Un molino de viento junto a un camino.]");
        });
        // El contexto mandado al modelo debe incluir el texto de la misma
        // pagina, no la imagen aislada (confirmado con prueba manual que
        // sin contexto el modelo adivina a ciegas).
        verify(anthropicClient).describirImagen(any(), anyString(), contains("En un lugar de la Mancha"));
    }

    @Test
    void ignoraImagenesPequenasSinLlamarAAnthropic() throws IOException {
        MockMultipartFile archivo =
                new MockMultipartFile("archivo", "manual.pdf", "application/pdf", pdfConImagen(50, 50));

        ExtractorTextoDocumento.ResultadoExtraccion resultado = extractor.extraer(archivo);

        assertThat(resultado.texto()).doesNotContain("Descripción de imagen");
        assertThat(resultado.imagenes()).isEmpty();
        verify(anthropicClient, org.mockito.Mockito.never()).describirImagen(any(), anyString(), anyString());
    }

    @Test
    void rechazaFormatosNoSoportados() {
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "manual.docx", "application/vnd.ms-word", "contenido".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> extractor.extraer(archivo))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void rechazaArchivosSinExtension() {
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "manual", "application/octet-stream", "contenido".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> extractor.extraer(archivo)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rechazaTextoVacioAunConExtensionValida() {
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "vacio.txt", "text/plain", "   ".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> extractor.extraer(archivo))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    private byte[] pdfDePrueba() throws IOException {
        try (PDDocument pdf = new PDDocument()) {
            PDPage pagina = new PDPage();
            pdf.addPage(pagina);
            try (PDPageContentStream contenido = new PDPageContentStream(pdf, pagina)) {
                contenido.beginText();
                contenido.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contenido.newLineAtOffset(50, 700);
                contenido.showText("En un lugar de la Mancha");
                contenido.endText();
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            pdf.save(salida);
            return salida.toByteArray();
        }
    }

    private byte[] pdfConImagen(int ancho, int alto) throws IOException {
        try (PDDocument pdf = new PDDocument()) {
            PDPage pagina = new PDPage();
            pdf.addPage(pagina);

            BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
            PDImageXObject imagenXObject = LosslessFactory.createFromImage(pdf, imagen);

            try (PDPageContentStream contenido = new PDPageContentStream(pdf, pagina)) {
                contenido.beginText();
                contenido.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contenido.newLineAtOffset(50, 700);
                contenido.showText("En un lugar de la Mancha");
                contenido.endText();
                contenido.drawImage(imagenXObject, 50, 400, ancho, alto);
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            pdf.save(salida);
            return salida.toByteArray();
        }
    }
}
