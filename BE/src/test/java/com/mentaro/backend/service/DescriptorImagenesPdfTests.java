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

            List<DescriptorImagenesPdf.ImagenDescrita> resultado = descriptor.describir(pdf, List.of("texto de la pagina"));

            assertThat(resultado).isEmpty();
            verify(anthropicClient, never()).describirImagen(any(), anyString(), anyString());
        }
    }

    @Test
    void describeImagenesGrandesUsandoElTextoDeLaMismaPaginaComoContextoYDevuelveLosBytes() throws IOException {
        when(anthropicClient.describirImagen(any(), anyString(), anyString())).thenReturn("un diagrama");
        try (PDDocument pdf = new PDDocument()) {
            PDPage pagina = new PDPage();
            pdf.addPage(pagina);
            agregarImagen(pdf, pagina, 200, 200);

            List<DescriptorImagenesPdf.ImagenDescrita> resultado =
                    descriptor.describir(pdf, List.of("texto de contexto de la pagina 0"));

            assertThat(resultado).hasSize(1);
            DescriptorImagenesPdf.ImagenDescrita imagen = resultado.getFirst();
            assertThat(imagen.pagina()).isZero();
            assertThat(imagen.descripcion()).isEqualTo("un diagrama");
            // Los bytes originales viajan junto con la descripcion - la
            // imagen SI se le muestra al usuario despues (ver correccion
            // del sistema de diseño sobre imagenes), no se descartan.
            assertThat(imagen.pngBytes()).isNotEmpty();
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
    void descartaImagenesClasificadasComoDecorativasSinPersistirlas() throws IOException {
        // Reproduce el problema real detectado probando: logos y membretes
        // institucionales (ej. el logo de la universidad que dicta el
        // curso) pasan el filtro de tamano sin problema, pero no aportan
        // nada al contenido - se descartan aca, no en la galeria.
        when(anthropicClient.describirImagen(any(), anyString(), anyString()))
                .thenReturn("RELEVANCIA: decorativo\nDESCRIPCION:");
        try (PDDocument pdf = new PDDocument()) {
            PDPage pagina = new PDPage();
            pdf.addPage(pagina);
            agregarImagen(pdf, pagina, 200, 200);

            List<DescriptorImagenesPdf.ImagenDescrita> resultado = descriptor.describir(pdf, List.of("contexto"));

            assertThat(resultado).isEmpty();
        }
    }

    @Test
    void conservaImagenesRelevantesYExtraeSoloElCampoDescripcion() throws IOException {
        when(anthropicClient.describirImagen(any(), anyString(), anyString()))
                .thenReturn("RELEVANCIA: relevante\nDESCRIPCION: Un diagrama de flujo del algoritmo.");
        try (PDDocument pdf = new PDDocument()) {
            PDPage pagina = new PDPage();
            pdf.addPage(pagina);
            agregarImagen(pdf, pagina, 200, 200);

            List<DescriptorImagenesPdf.ImagenDescrita> resultado = descriptor.describir(pdf, List.of("contexto"));

            assertThat(resultado).singleElement().satisfies(imagen ->
                    assertThat(imagen.descripcion()).isEqualTo("Un diagrama de flujo del algoritmo."));
        }
    }

    @Test
    void siElModeloNoSigueElFormatoEsperadoConservaLaImagenPorDefecto() throws IOException {
        // Parseo defensivo: una desviacion de formato no debe descartar
        // contenido util - se prefiere conservar (falla abierto).
        when(anthropicClient.describirImagen(any(), anyString(), anyString()))
                .thenReturn("Un diagrama sin el formato pedido.");
        try (PDDocument pdf = new PDDocument()) {
            PDPage pagina = new PDPage();
            pdf.addPage(pagina);
            agregarImagen(pdf, pagina, 200, 200);

            List<DescriptorImagenesPdf.ImagenDescrita> resultado = descriptor.describir(pdf, List.of("contexto"));

            assertThat(resultado).singleElement().satisfies(imagen ->
                    assertThat(imagen.descripcion()).isEqualTo("Un diagrama sin el formato pedido."));
        }
    }

    @Test
    void marcaEsencialCuandoElModeloLoIndica() throws IOException {
        when(anthropicClient.describirImagen(any(), anyString(), anyString()))
                .thenReturn("RELEVANCIA: relevante\nESENCIAL: si\nDESCRIPCION: Cruce con prioridad de paso.");
        try (PDDocument pdf = new PDDocument()) {
            PDPage pagina = new PDPage();
            pdf.addPage(pagina);
            agregarImagen(pdf, pagina, 200, 200);

            List<DescriptorImagenesPdf.ImagenDescrita> resultado = descriptor.describir(pdf, List.of("contexto"));

            assertThat(resultado).singleElement().satisfies(imagen -> assertThat(imagen.esEsencial()).isTrue());
        }
    }

    @Test
    void noMarcaEsencialCuandoElModeloDiceQueNo() throws IOException {
        when(anthropicClient.describirImagen(any(), anyString(), anyString()))
                .thenReturn("RELEVANCIA: relevante\nESENCIAL: no\nDESCRIPCION: Una ilustracion decorativa del tema.");
        try (PDDocument pdf = new PDDocument()) {
            PDPage pagina = new PDPage();
            pdf.addPage(pagina);
            agregarImagen(pdf, pagina, 200, 200);

            List<DescriptorImagenesPdf.ImagenDescrita> resultado = descriptor.describir(pdf, List.of("contexto"));

            assertThat(resultado).singleElement().satisfies(imagen -> assertThat(imagen.esEsencial()).isFalse());
        }
    }

    @Test
    void siElModeloNoSigueElFormatoEsperadoNoMarcaEsencialPorDefecto() throws IOException {
        // Parseo defensivo al reves de la relevancia a proposito: ante un
        // formato inesperado se prefiere "no esencial" (falla cerrado) - ver
        // comentario en DescriptorImagenesPdf.esEsencial.
        when(anthropicClient.describirImagen(any(), anyString(), anyString()))
                .thenReturn("Un diagrama sin el formato pedido.");
        try (PDDocument pdf = new PDDocument()) {
            PDPage pagina = new PDPage();
            pdf.addPage(pagina);
            agregarImagen(pdf, pagina, 200, 200);

            List<DescriptorImagenesPdf.ImagenDescrita> resultado = descriptor.describir(pdf, List.of("contexto"));

            assertThat(resultado).singleElement().satisfies(imagen -> assertThat(imagen.esEsencial()).isFalse());
        }
    }

    @Test
    void cadaImagenRecibeUnIdUnico() throws IOException {
        when(anthropicClient.describirImagen(any(), anyString(), anyString())).thenReturn("descripcion");
        try (PDDocument pdf = new PDDocument()) {
            PDPage pagina = new PDPage();
            pdf.addPage(pagina);
            agregarImagen(pdf, pagina, 200, 200);
            agregarImagen(pdf, pagina, 190, 190);

            List<DescriptorImagenesPdf.ImagenDescrita> resultado = descriptor.describir(pdf, List.of("contexto"));

            assertThat(resultado).extracting(DescriptorImagenesPdf.ImagenDescrita::id).doesNotHaveDuplicates();
            assertThat(resultado).allSatisfy(imagen -> assertThat(imagen.id()).isNotNull());
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

            List<DescriptorImagenesPdf.ImagenDescrita> resultado = descriptor.describir(pdf, List.of("contexto"));

            assertThat(resultado).extracting(DescriptorImagenesPdf.ImagenDescrita::descripcion)
                    .containsExactly("segunda imagen ok");
        }
    }
}
