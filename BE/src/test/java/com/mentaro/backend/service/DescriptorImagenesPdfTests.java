package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mentaro.backend.anthropic.AnthropicClient;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

class DescriptorImagenesPdfTests {

    private final AnthropicClient anthropicClient = mock(AnthropicClient.class);
    private final DescriptorImagenesPdf descriptor = new DescriptorImagenesPdf(anthropicClient);

    private void agregarImagen(PDDocument pdf, PDPage pagina, int ancho, int alto) throws IOException {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        PDImageXObject imagenXObject = LosslessFactory.createFromImage(pdf, imagen);
        try (PDPageContentStream contenido = new PDPageContentStream(pdf, pagina, PDPageContentStream.AppendMode.APPEND, true)) {
            contenido.drawImage(imagenXObject, 0, 0, ancho, alto);
        }
    }

    @Test
    void ignoraImagenesMasChicasQueElMinimo() throws IOException {
        when(anthropicClient.describirImagen(any(), anyString(), anyString())).thenReturn("descripcion");
        try (PDDocument pdf = new PDDocument()) {
            PDPage pagina = new PDPage();
            pdf.addPage(pagina);
            agregarImagen(pdf, pagina, 100, 100);

            Map<Integer, List<String>> resultado = descriptor.describir(pdf, List.of("texto de la pagina"));

            assertThat(resultado).isEmpty();
            verify(anthropicClient, never()).describirImagen(any(), anyString(), anyString());
        }
    }

    @Test
    void describeImagenesGrandesUsandoElTextoDeLaMismaPaginaComoContexto() throws IOException {
        when(anthropicClient.describirImagen(any(), anyString(), anyString())).thenReturn("un diagrama");
        try (PDDocument pdf = new PDDocument()) {
            PDPage pagina = new PDPage();
            pdf.addPage(pagina);
            agregarImagen(pdf, pagina, 200, 200);

            Map<Integer, List<String>> resultado = descriptor.describir(pdf, List.of("texto de contexto de la pagina 0"));

            assertThat(resultado).containsEntry(0, List.of("un diagrama"));
            verify(anthropicClient).describirImagen(any(), org.mockito.ArgumentMatchers.eq("image/png"),
                    org.mockito.ArgumentMatchers.contains("texto de contexto de la pagina 0"));
        }
    }

    @Test
    void limitaA30ImagenesPriorizandoLasMasGrandes() throws IOException {
        when(anthropicClient.describirImagen(any(), anyString(), anyString())).thenReturn("descripcion");
        try (PDDocument pdf = new PDDocument()) {
            PDPage pagina = new PDPage();
            pdf.addPage(pagina);
            // 35 imagenes de tamanos crecientes - solo las 30 mas grandes
            // (tamanos 151..185) deberian describirse.
            for (int i = 0; i < 35; i++) {
                agregarImagen(pdf, pagina, 151 + i, 151 + i);
            }

            descriptor.describir(pdf, List.of("contexto"));

            verify(anthropicClient, times(30)).describirImagen(any(), anyString(), anyString());
        }
    }

    @Test
    void unaImagenQueFallaNoBloqueaElRestoDelLote() throws IOException {
        when(anthropicClient.describirImagen(any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Anthropic no respondio"))
                .thenReturn("segunda imagen ok");
        try (PDDocument pdf = new PDDocument()) {
            PDPage pagina = new PDPage();
            pdf.addPage(pagina);
            agregarImagen(pdf, pagina, 200, 200);
            agregarImagen(pdf, pagina, 190, 190);

            Map<Integer, List<String>> resultado = descriptor.describir(pdf, List.of("contexto"));

            assertThat(resultado.get(0)).containsExactly("segunda imagen ok");
        }
    }
}
