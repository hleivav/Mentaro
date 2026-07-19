package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class DocumentoImagenTemporalServiceTests {

    @Autowired
    private DocumentoImagenTemporalService service;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private DocumentoRepository documentoRepository;

    private UUID crearDocumento() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-img-" + UUID.randomUUID(), "x@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.PROCESANDO));
        return documento.getId();
    }

    @Test
    void guardaVariasImagenesRespetandoElOrdenDePagina() {
        UUID documentoId = crearDocumento();
        service.guardar(documentoId, List.of(
                new DescriptorImagenesPdf.ImagenDescrita(UUID.randomUUID(), 2, "tercera pagina", new byte[] {1}, false),
                new DescriptorImagenesPdf.ImagenDescrita(UUID.randomUUID(), 0, "primera pagina", new byte[] {2}, false)));

        List<DocumentoImagenTemporal> imagenes = service.listar(documentoId);

        assertThat(imagenes).extracting(DocumentoImagenTemporal::getPagina).containsExactly(0, 2);
        assertThat(imagenes).extracting(DocumentoImagenTemporal::getDescripcion)
                .containsExactly("primera pagina", "tercera pagina");
    }

    @Test
    void obtenerLanza404SiLaImagenNoEsDeEseDocumento() {
        UUID documentoId = crearDocumento();
        UUID otroDocumentoId = crearDocumento();
        service.guardar(documentoId, List.of(
                new DescriptorImagenesPdf.ImagenDescrita(UUID.randomUUID(), 0, "desc", new byte[] {1}, false)));
        UUID imagenId = service.listar(documentoId).getFirst().getId();

        assertThatThrownBy(() -> service.obtener(otroDocumentoId, imagenId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void obtenerDevuelveLaImagenSiPerteneceAlDocumento() {
        UUID documentoId = crearDocumento();
        service.guardar(documentoId, List.of(
                new DescriptorImagenesPdf.ImagenDescrita(UUID.randomUUID(), 0, "desc", new byte[] {9, 8, 7}, false)));
        UUID imagenId = service.listar(documentoId).getFirst().getId();

        DocumentoImagenTemporal imagen = service.obtener(documentoId, imagenId);

        assertThat(imagen.getImagenBytes()).containsExactly(9, 8, 7);
    }
}
