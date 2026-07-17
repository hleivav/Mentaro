package com.mentaro.backend.service;

import com.mentaro.backend.anthropic.AnthropicClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Describe las imagenes embebidas en un PDF via Anthropic (DeepSeek no
// acepta imagenes) para que la Pasada A pueda "entender" diagramas sin
// verlos - las descripciones se insertan como texto, la imagen misma
// nunca se persiste (ver ExtractorTextoDocumento).
@Service
public class DescriptorImagenesPdf {

    private static final Logger log = LoggerFactory.getLogger(DescriptorImagenesPdf.class);

    // Bajo estos dos numeros, casi siempre es un logo, vineta o elemento
    // decorativo, no un diagrama explicativo - filtro barato (dimensiones,
    // sin IA) antes del paso caro.
    private static final int ANCHO_MINIMO = 150;
    private static final int ALTO_MINIMO = 150;
    // Limite duro: evita que un documento con cientos de imagenes dispare
    // un costo descontrolado. Si hay mas candidatas que esto despues del
    // filtro de tamano, se describen solo las N mas grandes (una imagen
    // mas grande es mas probable que sea un diagrama importante que un
    // icono).
    private static final int MAX_IMAGENES_POR_DOCUMENTO = 30;

    private static final String PROMPT = """
            Este es un fragmento de un documento. Usa el texto de contexto para
            entender de qué trata la sección, y luego describe brevemente qué
            muestra la imagen y qué concepto explica dentro de ese contexto,
            en 2-4 líneas. No describas el estilo visual ni el diseño.

            Contexto (texto antes y después de la imagen):
            \"\"\"
            %s
            \"\"\"
            """;

    private final AnthropicClient anthropicClient;

    public DescriptorImagenesPdf(AnthropicClient anthropicClient) {
        this.anthropicClient = anthropicClient;
    }

    // paginasTexto: texto ya extraido de cada pagina (mismo indice que las
    // paginas del PDF), usado como contexto para anclar la descripcion en
    // lo que esa seccion ya esta explicando - describir la imagen aislada
    // produce descripciones plausibles pero a veces conceptualmente
    // equivocadas (confirmado con una prueba manual real).
    public Map<Integer, List<String>> describir(PDDocument pdf, List<String> paginasTexto) throws IOException {
        List<ImagenConPagina> seleccionadas = extraerCandidatas(pdf).stream()
                .sorted(Comparator.comparingLong(ImagenConPagina::area).reversed())
                .limit(MAX_IMAGENES_POR_DOCUMENTO)
                .toList();

        Map<Integer, List<String>> descripcionesPorPagina = new LinkedHashMap<>();
        for (ImagenConPagina imagen : seleccionadas) {
            String contexto = paginasTexto.get(imagen.pagina());
            try {
                String descripcion = anthropicClient.describirImagen(
                        imagen.pngBytes(), "image/png", PROMPT.formatted(contexto));
                descripcionesPorPagina.computeIfAbsent(imagen.pagina(), k -> new ArrayList<>()).add(descripcion);
            } catch (Exception e) {
                // Una imagen puntual que falla (red, formato raro, etc.) no
                // debe tumbar la ingesta completa del documento.
                log.warn("No se pudo describir una imagen de la pagina {}: {}", imagen.pagina() + 1, e.getMessage());
            }
        }
        return descripcionesPorPagina;
    }

    private List<ImagenConPagina> extraerCandidatas(PDDocument pdf) throws IOException {
        List<ImagenConPagina> candidatas = new ArrayList<>();
        for (int pagina = 0; pagina < pdf.getNumberOfPages(); pagina++) {
            PDPage page = pdf.getPage(pagina);
            PDResources recursos = page.getResources();
            if (recursos == null) {
                continue;
            }
            for (COSName nombre : recursos.getXObjectNames()) {
                PDXObject xObject = recursos.getXObject(nombre);
                if (!(xObject instanceof PDImageXObject imagenXObject)) {
                    continue;
                }
                int ancho = imagenXObject.getWidth();
                int alto = imagenXObject.getHeight();
                if (ancho < ANCHO_MINIMO || alto < ALTO_MINIMO) {
                    continue;
                }
                ByteArrayOutputStream salida = new ByteArrayOutputStream();
                ImageIO.write(imagenXObject.getImage(), "png", salida);
                candidatas.add(new ImagenConPagina(pagina, ancho, alto, salida.toByteArray()));
            }
        }
        return candidatas;
    }

    private record ImagenConPagina(int pagina, int ancho, int alto, byte[] pngBytes) {
        long area() {
            return (long) ancho * alto;
        }
    }
}
