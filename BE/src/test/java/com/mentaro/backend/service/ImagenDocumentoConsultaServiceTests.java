package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentaro.backend.dto.ImagenDocumentoDTO;
import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.DocumentoImagenTemporal;
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
class ImagenDocumentoConsultaServiceTests {

    @Autowired
    private ImagenDocumentoConsultaService imagenDocumentoConsultaService;
    @Autowired
    private DocumentoImagenTemporalService imagenTemporalService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private DocumentoRepository documentoRepository;

    @Test
    void listarDevuelveLasImagenesDelDocumentoDelDueno() {
        Usuario dueno = usuarioRepository.save(new Usuario("firebase-uid-imgc-dueno-" + UUID.randomUUID(), "d@example.com"));
        Documento documento = documentoRepository.save(new Documento(dueno, "Doc", EstadoDocumento.LISTO));
        imagenTemporalService.guardar(documento.getId(),
                List.of(new DescriptorImagenesPdf.ImagenDescrita(0, "un diagrama", new byte[] {1})));

        List<ImagenDocumentoDTO> imagenes = imagenDocumentoConsultaService.listar(dueno, documento.getId());

        assertThat(imagenes).singleElement().satisfies(imagen -> {
            assertThat(imagen.pagina()).isZero();
            assertThat(imagen.descripcion()).isEqualTo("un diagrama");
        });
    }

    @Test
    void listarLanza403SiElDocumentoNoPerteneceAlUsuario() {
        Usuario dueno = usuarioRepository.save(new Usuario("firebase-uid-imgc-dueno2-" + UUID.randomUUID(), "d2@example.com"));
        Usuario otro = usuarioRepository.save(new Usuario("firebase-uid-imgc-otro-" + UUID.randomUUID(), "o@example.com"));
        Documento documento = documentoRepository.save(new Documento(dueno, "Doc", EstadoDocumento.LISTO));

        assertThatThrownBy(() -> imagenDocumentoConsultaService.listar(otro, documento.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void obtenerLanza403SiElDocumentoNoPerteneceAlUsuarioAunqueLaImagenExista() {
        Usuario dueno = usuarioRepository.save(new Usuario("firebase-uid-imgc-dueno3-" + UUID.randomUUID(), "d3@example.com"));
        Usuario otro = usuarioRepository.save(new Usuario("firebase-uid-imgc-otro2-" + UUID.randomUUID(), "o2@example.com"));
        Documento documento = documentoRepository.save(new Documento(dueno, "Doc", EstadoDocumento.LISTO));
        imagenTemporalService.guardar(documento.getId(),
                List.of(new DescriptorImagenesPdf.ImagenDescrita(0, "desc", new byte[] {1})));
        UUID imagenId = imagenTemporalService.listar(documento.getId()).getFirst().getId();

        assertThatThrownBy(() -> imagenDocumentoConsultaService.obtener(otro, documento.getId(), imagenId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void obtenerDevuelveLaImagenSiElDuenoCoincide() {
        Usuario dueno = usuarioRepository.save(new Usuario("firebase-uid-imgc-dueno4-" + UUID.randomUUID(), "d4@example.com"));
        Documento documento = documentoRepository.save(new Documento(dueno, "Doc", EstadoDocumento.LISTO));
        imagenTemporalService.guardar(documento.getId(),
                List.of(new DescriptorImagenesPdf.ImagenDescrita(0, "desc", new byte[] {5, 6})));
        UUID imagenId = imagenTemporalService.listar(documento.getId()).getFirst().getId();

        DocumentoImagenTemporal imagen = imagenDocumentoConsultaService.obtener(dueno, documento.getId(), imagenId);

        assertThat(imagen.getImagenBytes()).containsExactly(5, 6);
    }
}
