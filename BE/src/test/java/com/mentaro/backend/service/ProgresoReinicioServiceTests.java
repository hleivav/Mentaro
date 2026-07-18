package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.EstadoGeneracion;
import com.mentaro.backend.entity.EstadoResultado;
import com.mentaro.backend.entity.NivelImportancia;
import com.mentaro.backend.entity.ProgresoUsuario;
import com.mentaro.backend.entity.ResultadoUnidad;
import com.mentaro.backend.entity.Seccion;
import com.mentaro.backend.entity.TipoContenido;
import com.mentaro.backend.entity.Unidad;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.ProgresoUsuarioRepository;
import com.mentaro.backend.repository.ResultadoUnidadRepository;
import com.mentaro.backend.repository.SeccionRepository;
import com.mentaro.backend.repository.UnidadRepository;
import com.mentaro.backend.repository.UsuarioRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class ProgresoReinicioServiceTests {

    @Autowired
    private ProgresoReinicioService progresoReinicioService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private DocumentoRepository documentoRepository;
    @Autowired
    private SeccionRepository seccionRepository;
    @Autowired
    private UnidadRepository unidadRepository;
    @Autowired
    private ProgresoUsuarioRepository progresoUsuarioRepository;
    @Autowired
    private ResultadoUnidadRepository resultadoUnidadRepository;

    @Test
    void borraSoloElProgresoDeEseUsuarioEnEseDocumentoSinTocarElContenido() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-reinicio-" + UUID.randomUUID(), "x@example.com"));
        Usuario otroUsuario = usuarioRepository.save(new Usuario("firebase-uid-reinicio-otro-" + UUID.randomUUID(), "y@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.LISTO));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad unidad = new Unidad(documento, seccion, "U1", TipoContenido.DECLARATIVO, NivelImportancia.ESENCIAL);
        unidad.asignarContenido("corta", "alt", "{\"enunciado\":\"e\",\"alternativas\":[\"a\"],\"correcta_index\":0}",
                "{\"enunciado\":\"e\",\"alternativas\":[\"a\"],\"correcta_index\":0}", EstadoGeneracion.GENERADA);
        unidad = unidadRepository.save(unidad);

        ProgresoUsuario progreso = progresoUsuarioRepository.save(new ProgresoUsuario(usuario, documento));
        progreso.setPosicionActual(100);
        progresoUsuarioRepository.save(progreso);
        resultadoUnidadRepository.save(new ResultadoUnidad(usuario, unidad, EstadoResultado.DOMINADA));

        // Progreso de otro usuario en el MISMO documento - no debe tocarse.
        ProgresoUsuario progresoOtro = progresoUsuarioRepository.save(new ProgresoUsuario(otroUsuario, documento));
        progresoOtro.setPosicionActual(200);
        progresoUsuarioRepository.save(progresoOtro);
        resultadoUnidadRepository.save(new ResultadoUnidad(otroUsuario, unidad, EstadoResultado.DOMINADA));

        UUID documentoId = documento.getId();
        UUID unidadId = unidad.getId();

        progresoReinicioService.reiniciar(usuario, documentoId);

        assertThat(progresoUsuarioRepository.findByUsuario_IdAndDocumento_Id(usuario.getId(), documentoId)).isEmpty();
        assertThat(resultadoUnidadRepository.findByUsuario_IdAndUnidad_Id(usuario.getId(), unidadId)).isEmpty();

        // Del otro usuario no se toca nada.
        assertThat(progresoUsuarioRepository.findByUsuario_IdAndDocumento_Id(otroUsuario.getId(), documentoId)).isPresent();
        assertThat(resultadoUnidadRepository.findByUsuario_IdAndUnidad_Id(otroUsuario.getId(), unidadId)).isPresent();

        // El contenido generado sigue intacto.
        assertThat(documentoRepository.findById(documentoId)).isPresent();
        assertThat(seccionRepository.findByDocumento_Id(documentoId)).isNotEmpty();
        assertThat(unidadRepository.findByDocumento_Id(documentoId)).isNotEmpty();
    }

    @Test
    void lanza403SiElDocumentoNoPerteneceAlUsuario() {
        Usuario dueno = usuarioRepository.save(new Usuario("firebase-uid-reinicio-dueno-" + UUID.randomUUID(), "d@example.com"));
        Usuario otro = usuarioRepository.save(new Usuario("firebase-uid-reinicio-otro2-" + UUID.randomUUID(), "o@example.com"));
        Documento documento = documentoRepository.save(new Documento(dueno, "Doc", EstadoDocumento.LISTO));

        assertThatThrownBy(() -> progresoReinicioService.reiniciar(otro, documento.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void lanza404SiElDocumentoNoExiste() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-reinicio-404-" + UUID.randomUUID(), "z@example.com"));

        assertThatThrownBy(() -> progresoReinicioService.reiniciar(usuario, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
