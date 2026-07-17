package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.UsuarioRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class DocumentoConsultaServiceTests {

    @Autowired
    private DocumentoConsultaService documentoConsultaService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private DocumentoRepository documentoRepository;

    private Usuario crearUsuario() {
        return usuarioRepository.save(new Usuario("firebase-uid-consulta-" + UUID.randomUUID(), "x@example.com"));
    }

    @Test
    void devuelveElDocumentoSiPerteneceAlUsuario() {
        Usuario usuario = crearUsuario();
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.PROCESANDO));

        assertThat(documentoConsultaService.obtener(usuario, documento.getId())).isEqualTo(documento);
    }

    @Test
    void lanza404SiElDocumentoNoExiste() {
        Usuario usuario = crearUsuario();

        assertThatThrownBy(() -> documentoConsultaService.obtener(usuario, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void lanza403SiElDocumentoPerteneceAOtroUsuario() {
        Usuario dueno = crearUsuario();
        Usuario otro = crearUsuario();
        Documento documento = documentoRepository.save(new Documento(dueno, "Doc", EstadoDocumento.PROCESANDO));

        assertThatThrownBy(() -> documentoConsultaService.obtener(otro, documento.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void noExigeNingunEstadoEnParticularADiferenciaDeSesionService() {
        Usuario usuario = crearUsuario();
        for (EstadoDocumento estado : EstadoDocumento.values()) {
            Documento documento = documentoRepository.save(new Documento(usuario, "Doc " + estado, estado));
            assertThat(documentoConsultaService.obtener(usuario, documento.getId()).getEstado()).isEqualTo(estado);
        }
    }

    @Test
    void listarDevuelveSoloLosDocumentosDelUsuarioOrdenadosPorMasReciente() throws InterruptedException {
        Usuario usuario = crearUsuario();
        Usuario otro = crearUsuario();
        Documento primero = documentoRepository.save(new Documento(usuario, "Primero", EstadoDocumento.LISTO));
        Thread.sleep(5);
        Documento segundo = documentoRepository.save(new Documento(usuario, "Segundo", EstadoDocumento.MAPEADO));
        documentoRepository.save(new Documento(otro, "De otro usuario", EstadoDocumento.LISTO));

        List<Documento> resultado = documentoConsultaService.listar(usuario);

        assertThat(resultado).extracting(Documento::getId).containsExactly(segundo.getId(), primero.getId());
    }

    @Test
    void listarDevuelveVacioSiElUsuarioNoTieneDocumentos() {
        Usuario usuario = crearUsuario();

        assertThat(documentoConsultaService.listar(usuario)).isEmpty();
    }
}
