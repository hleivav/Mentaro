package com.mentaro.backend.controller;

import com.mentaro.backend.dto.DocumentoResponse;
import com.mentaro.backend.dto.GenerarRequest;
import com.mentaro.backend.dto.MapaDocumentoResponse;
import com.mentaro.backend.dto.ProgresoDocumentoResponse;
import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.service.DocumentoConsultaService;
import com.mentaro.backend.service.DocumentoEliminacionService;
import com.mentaro.backend.service.GeneracionDocumentoService;
import com.mentaro.backend.service.IngestaDocumentoService;
import com.mentaro.backend.service.MapaDocumentoConsultaService;
import com.mentaro.backend.service.ProgresoDocumentoService;
import com.mentaro.backend.service.ProgresoReinicioService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documentos")
public class DocumentoController {

    private final IngestaDocumentoService ingestaDocumentoService;
    private final DocumentoConsultaService documentoConsultaService;
    private final GeneracionDocumentoService generacionDocumentoService;
    private final MapaDocumentoConsultaService mapaDocumentoConsultaService;
    private final ProgresoDocumentoService progresoDocumentoService;
    private final DocumentoEliminacionService documentoEliminacionService;
    private final ProgresoReinicioService progresoReinicioService;

    public DocumentoController(
            IngestaDocumentoService ingestaDocumentoService,
            DocumentoConsultaService documentoConsultaService,
            GeneracionDocumentoService generacionDocumentoService,
            MapaDocumentoConsultaService mapaDocumentoConsultaService,
            ProgresoDocumentoService progresoDocumentoService,
            DocumentoEliminacionService documentoEliminacionService,
            ProgresoReinicioService progresoReinicioService) {
        this.ingestaDocumentoService = ingestaDocumentoService;
        this.documentoConsultaService = documentoConsultaService;
        this.generacionDocumentoService = generacionDocumentoService;
        this.mapaDocumentoConsultaService = mapaDocumentoConsultaService;
        this.progresoDocumentoService = progresoDocumentoService;
        this.documentoEliminacionService = documentoEliminacionService;
        this.progresoReinicioService = progresoReinicioService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoResponse subir(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(name = "titulo", required = false) String titulo) {
        Documento documento = ingestaDocumentoService.crear(usuario, archivo, titulo);
        return DocumentoResponse.from(documento);
    }

    @GetMapping
    public List<DocumentoResponse> listar(@AuthenticationPrincipal Usuario usuario) {
        return documentoConsultaService.listar(usuario).stream().map(DocumentoResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DocumentoResponse obtener(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id) {
        return DocumentoResponse.from(documentoConsultaService.obtener(usuario, id));
    }

    @PostMapping("/{id}/generar")
    public DocumentoResponse generar(
            @AuthenticationPrincipal Usuario usuario, @PathVariable UUID id, @Valid @RequestBody GenerarRequest request) {
        return DocumentoResponse.from(generacionDocumentoService.generar(usuario, id, request.unidadIds()));
    }

    @GetMapping("/{id}/mapa")
    public MapaDocumentoResponse mapa(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id) {
        return mapaDocumentoConsultaService.obtenerMapa(usuario, id);
    }

    @GetMapping("/{id}/progreso")
    public ProgresoDocumentoResponse progreso(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id) {
        return progresoDocumentoService.obtenerProgreso(usuario, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id) {
        documentoEliminacionService.eliminar(usuario, id);
    }

    @DeleteMapping("/{id}/progreso")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reiniciarProgreso(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id) {
        progresoReinicioService.reiniciar(usuario, id);
    }
}
