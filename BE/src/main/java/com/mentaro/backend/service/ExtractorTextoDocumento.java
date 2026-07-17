package com.mentaro.backend.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

// Extrae el texto plano de un archivo subido. Soporta .txt y .pdf - son
// los formatos que cubren el catalogo curado (Gutenberg, dominio publico)
// y el caso de uso principal (libros/manuales escaneados con capa de
// texto). Si en el futuro se necesita mas (epub, docx), agregar aca.
@Component
public class ExtractorTextoDocumento {

    private final DescriptorImagenesPdf descriptorImagenesPdf;

    public ExtractorTextoDocumento(DescriptorImagenesPdf descriptorImagenesPdf) {
        this.descriptorImagenesPdf = descriptorImagenesPdf;
    }

    public String extraer(MultipartFile archivo) {
        String extension = extension(archivo.getOriginalFilename());
        String texto = switch (extension) {
            case "txt" -> extraerTexto(archivo);
            case "pdf" -> extraerPdf(archivo);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato no soportado: ." + extension + " (solo .txt y .pdf por ahora)");
        };

        if (texto.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se pudo extraer texto del archivo (¿esta vacio o es un PDF escaneado sin capa de texto?)");
        }
        return texto;
    }

    private String extension(String nombreArchivo) {
        if (nombreArchivo == null || !nombreArchivo.contains(".")) {
            return "";
        }
        return nombreArchivo.substring(nombreArchivo.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String extraerTexto(MultipartFile archivo) {
        try {
            return new String(archivo.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo: " + e.getMessage());
        }
    }

    // Extrae texto PAGINA POR PAGINA (no todo el documento de una vez) para
    // poder correlacionar cada imagen embebida con el texto de su misma
    // pagina, que sirve de contexto para describirla (ver
    // DescriptorImagenesPdf) y como punto de insercion de la descripcion -
    // PDFBox no permite un posicionamiento mas fino dentro de una pagina
    // de forma confiable, asi que no se intenta.
    private String extraerPdf(MultipartFile archivo) {
        try (PDDocument pdf = Loader.loadPDF(archivo.getBytes())) {
            List<String> textoPorPagina = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int pagina = 1; pagina <= pdf.getNumberOfPages(); pagina++) {
                stripper.setStartPage(pagina);
                stripper.setEndPage(pagina);
                textoPorPagina.add(stripper.getText(pdf));
            }

            Map<Integer, List<String>> descripcionesPorPagina = descriptorImagenesPdf.describir(pdf, textoPorPagina);

            StringBuilder resultado = new StringBuilder();
            for (int pagina = 0; pagina < textoPorPagina.size(); pagina++) {
                resultado.append(textoPorPagina.get(pagina));
                for (String descripcion : descripcionesPorPagina.getOrDefault(pagina, List.of())) {
                    resultado.append("\n[Descripción de imagen: ").append(descripcion).append("]\n");
                }
            }
            return resultado.toString();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el PDF: " + e.getMessage());
        }
    }
}
