package com.mentaro.backend.service;

import com.mentaro.backend.anthropic.AnthropicClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
// verlos - las descripciones se insertan como texto (ver
// ExtractorTextoDocumento) y la imagen original se persiste de forma
// transitoria para mostrarsela al usuario (ver DocumentoImagenTemporalService).
// Solo se conservan las clasificadas como contenido explicativo relevante -
// logos, membretes y demas material institucional de la plataforma que
// aloja el documento se descartan (ver esDecorativa).
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

    private static final String MARCADOR_RELEVANCIA = "RELEVANCIA:";
    private static final String MARCADOR_DECORATIVO = "DECORATIVO";
    private static final String MARCADOR_DESCRIPCION = "DESCRIPCION:";

    private static final String PROMPT = """
            Este es un fragmento de un documento educativo. Usa el texto de
            contexto para entender de qué trata la sección.

            Primero, decidí si la imagen es contenido explicativo relevante
            para enseñar el tema (un diagrama, gráfico, ilustración
            conceptual, captura de una demostración) o si es un elemento
            institucional o decorativo sin valor educativo propio (un logo,
            membrete, foto de portada, elemento de diseño de la plataforma
            que aloja el documento). Si tenés dudas, preferí "relevante"
            antes que descartar contenido útil.

            Respondé EXACTAMENTE en este formato, sin texto adicional:

            RELEVANCIA: relevante | decorativo
            DESCRIPCION: <si es relevante, describí brevemente qué muestra
            la imagen y qué concepto explica dentro de ese contexto, en 2-4
            líneas, sin describir el estilo visual ni el diseño. Si es
            decorativo, dejá este campo vacío.>

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
    //
    // Devuelve tambien los bytes de la imagen (no solo la descripcion): a
    // diferencia de la primera version, la imagen original SI se le
    // muestra al usuario mientras juega (ver DocumentoImagenTemporalService)
    // - la version anterior los descartaba por una cautela de copyright
    // que resulto excesiva, la imagen es solo un archivo que se renderiza
    // como cualquier otro.
    public List<ImagenDescrita> describir(PDDocument pdf, List<String> paginasTexto) throws IOException {
        List<ImagenConPagina> seleccionadas = extraerCandidatas(pdf).stream()
                .sorted(Comparator.comparingLong(ImagenConPagina::area).reversed())
                .limit(MAX_IMAGENES_POR_DOCUMENTO)
                .toList();

        List<ImagenDescrita> resultado = new ArrayList<>();
        for (ImagenConPagina imagen : seleccionadas) {
            String contexto = paginasTexto.get(imagen.pagina());
            try {
                String respuesta = anthropicClient.describirImagen(
                        imagen.pngBytes(), "image/png", PROMPT.formatted(contexto));
                if (esDecorativa(respuesta)) {
                    // Logos, membretes, fotos de portada de la plataforma que
                    // aloja el documento (ej. el logo de la universidad que
                    // dicta el curso) - no aportan nada al contenido y
                    // confundian tanto la galeria de imagenes como el texto
                    // que lee la Pasada A (problema real detectado probando).
                    log.info("Imagen de la pagina {} descartada por decorativa/institucional (no explicativa)",
                            imagen.pagina() + 1);
                    continue;
                }
                resultado.add(new ImagenDescrita(imagen.pagina(), extraerDescripcion(respuesta), imagen.pngBytes()));
            } catch (Exception e) {
                // Una imagen puntual que falla (red, formato raro, etc.) no
                // debe tumbar la ingesta completa del documento.
                log.warn("No se pudo describir una imagen de la pagina {}: {}", imagen.pagina() + 1, e.getMessage());
            }
        }
        return resultado;
    }

    // Parseo defensivo a proposito: si el modelo no sigue el formato pedido
    // al pie de la letra, se prefiere conservar la imagen (falla abierto,
    // igual que indica el prompt) antes que descartar contenido util por
    // una desviacion de formato.
    private boolean esDecorativa(String respuesta) {
        String primeraLinea = respuesta.strip().lines().findFirst().orElse("");
        int indice = primeraLinea.toUpperCase(Locale.ROOT).indexOf(MARCADOR_RELEVANCIA);
        if (indice < 0) {
            return false;
        }
        return primeraLinea.substring(indice + MARCADOR_RELEVANCIA.length())
                .toUpperCase(Locale.ROOT)
                .contains(MARCADOR_DECORATIVO);
    }

    private String extraerDescripcion(String respuesta) {
        int indice = respuesta.toUpperCase(Locale.ROOT).indexOf(MARCADOR_DESCRIPCION);
        if (indice < 0) {
            return respuesta.strip();
        }
        return respuesta.substring(indice + MARCADOR_DESCRIPCION.length()).strip();
    }

    public record ImagenDescrita(int pagina, String descripcion, byte[] pngBytes) {
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
