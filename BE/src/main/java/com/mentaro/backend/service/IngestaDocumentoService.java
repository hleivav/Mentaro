package com.mentaro.backend.service;

import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.DocumentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// Orquesta la subida: extrae el texto (sincrono, para fallar rapido con un
// error claro si el archivo no sirve), crea el Documento y guarda el texto
// temporal, y recien despues dispara la Pasada A en segundo plano - el
// usuario no espera a que termine para recibir el id del documento y
// arrancar el polling (ver useDocumento en el doc de frontend).
//
// No hay una transaccion propia envolviendo todo el metodo: cada guardado
// (documentoRepository.save, textoTemporalService.guardar) ya es
// transaccional por si solo al ser una llamada a otro bean, y el runner
// async solo se dispara despues de que esos guardados ya se confirmaron.
@Service
public class IngestaDocumentoService {

    private final ExtractorTextoDocumento extractor;
    private final DocumentoRepository documentoRepository;
    private final DocumentoTextoTemporalService textoTemporalService;
    private final PasadaAAsyncRunner pasadaAAsyncRunner;

    public IngestaDocumentoService(
            ExtractorTextoDocumento extractor,
            DocumentoRepository documentoRepository,
            DocumentoTextoTemporalService textoTemporalService,
            PasadaAAsyncRunner pasadaAAsyncRunner) {
        this.extractor = extractor;
        this.documentoRepository = documentoRepository;
        this.textoTemporalService = textoTemporalService;
        this.pasadaAAsyncRunner = pasadaAAsyncRunner;
    }

    public Documento crear(Usuario usuario, MultipartFile archivo, String titulo) {
        String textoExtraido = extractor.extraer(archivo);

        Documento documento = documentoRepository.save(
                new Documento(usuario, tituloEfectivo(titulo, archivo), EstadoDocumento.PROCESANDO));
        textoTemporalService.guardar(documento.getId(), textoExtraido);

        pasadaAAsyncRunner.ejecutar(documento.getId(), textoExtraido);

        return documento;
    }

    private String tituloEfectivo(String titulo, MultipartFile archivo) {
        if (titulo != null && !titulo.isBlank()) {
            return titulo;
        }
        String nombreArchivo = archivo.getOriginalFilename();
        if (nombreArchivo == null || nombreArchivo.isBlank()) {
            return "Documento sin titulo";
        }
        int puntoExtension = nombreArchivo.lastIndexOf('.');
        return puntoExtension > 0 ? nombreArchivo.substring(0, puntoExtension) : nombreArchivo;
    }
}
