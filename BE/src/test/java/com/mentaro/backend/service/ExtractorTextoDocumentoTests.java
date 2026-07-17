package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class ExtractorTextoDocumentoTests {

    private final ExtractorTextoDocumento extractor = new ExtractorTextoDocumento();

    @Test
    void extraeTextoPlanoDeUnArchivoTxt() {
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "capitulo1.txt", "text/plain", "En un lugar de la Mancha...".getBytes(StandardCharsets.UTF_8));

        assertThat(extractor.extraer(archivo)).isEqualTo("En un lugar de la Mancha...");
    }

    @Test
    void extraeTextoDeUnPdfConCapaDeTexto() throws IOException {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "manual.pdf", "application/pdf", pdfDePrueba());

        assertThat(extractor.extraer(archivo)).contains("En un lugar de la Mancha");
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
}
