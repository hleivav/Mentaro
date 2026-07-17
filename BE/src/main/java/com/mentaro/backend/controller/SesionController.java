package com.mentaro.backend.controller;

import com.mentaro.backend.dto.ResponderRequest;
import com.mentaro.backend.dto.ResponderResponse;
import com.mentaro.backend.dto.SesionResponse;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.service.SesionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documentos/{documentoId}/sesion")
public class SesionController {

    private final SesionService sesionService;

    public SesionController(SesionService sesionService) {
        this.sesionService = sesionService;
    }

    @GetMapping
    public SesionResponse obtenerSesion(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID documentoId) {
        return sesionService.obtenerSesion(usuario, documentoId);
    }

    @PostMapping("/responder")
    public ResponderResponse responder(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable UUID documentoId,
            @Valid @RequestBody ResponderRequest request) {
        return sesionService.responder(usuario, documentoId, request);
    }
}
