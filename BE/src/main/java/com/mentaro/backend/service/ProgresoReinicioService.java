package com.mentaro.backend.service;

import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.ProgresoUsuarioRepository;
import com.mentaro.backend.repository.ResultadoUnidadRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Reinicia el progreso de un usuario en un documento SIN regenerar nada -
// borra solo progreso_usuario y resultado_unidad (el "donde estoy" y "que
// domino"), nunca unidades/secciones/secuencia_tablero (el contenido ya
// generado por DeepSeek, que no tiene sentido volver a pagar). Pensado
// originalmente para poder reprobar cambios de UI sin recargar el
// documento entero, pero es una funcion real: un usuario puede querer
// rejugar un documento desde cero.
@Service
public class ProgresoReinicioService {

    private final DocumentoConsultaService documentoConsultaService;
    private final ProgresoUsuarioRepository progresoUsuarioRepository;
    private final ResultadoUnidadRepository resultadoUnidadRepository;

    public ProgresoReinicioService(
            DocumentoConsultaService documentoConsultaService,
            ProgresoUsuarioRepository progresoUsuarioRepository,
            ResultadoUnidadRepository resultadoUnidadRepository) {
        this.documentoConsultaService = documentoConsultaService;
        this.progresoUsuarioRepository = progresoUsuarioRepository;
        this.resultadoUnidadRepository = resultadoUnidadRepository;
    }

    @Transactional
    public void reiniciar(Usuario usuario, UUID documentoId) {
        documentoConsultaService.obtener(usuario, documentoId);

        resultadoUnidadRepository.deleteByUsuario_IdAndUnidad_Documento_Id(usuario.getId(), documentoId);
        progresoUsuarioRepository.deleteByUsuario_IdAndDocumento_Id(usuario.getId(), documentoId);
    }
}
