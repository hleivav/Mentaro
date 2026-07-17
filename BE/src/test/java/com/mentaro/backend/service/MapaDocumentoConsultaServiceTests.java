package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.mentaro.backend.dto.MapaDocumentoResponse;
import com.mentaro.backend.dto.SeccionMapaDTO;
import com.mentaro.backend.dto.UnidadMapaDTO;
import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.NivelImportancia;
import com.mentaro.backend.entity.Seccion;
import com.mentaro.backend.entity.TipoContenido;
import com.mentaro.backend.entity.Unidad;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.DocumentoRepository;
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
class MapaDocumentoConsultaServiceTests {

    @Autowired
    private MapaDocumentoConsultaService mapaDocumentoConsultaService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private DocumentoRepository documentoRepository;
    @Autowired
    private SeccionRepository seccionRepository;
    @Autowired
    private UnidadRepository unidadRepository;

    private Usuario usuario;
    private Documento documento;

    private void inicializar(EstadoDocumento estado) {
        usuario = usuarioRepository.save(new Usuario("firebase-uid-mapa-" + UUID.randomUUID(), "x@example.com"));
        documento = documentoRepository.save(new Documento(usuario, "Doc", estado));
    }

    @Test
    void devuelveElArbolConPadreIdYLasUnidadesDirectasSoloDeclarativas() {
        inicializar(EstadoDocumento.MAPEADO);
        Seccion raiz = seccionRepository.save(new Seccion(documento, null, "Raiz", "resumen raiz"));
        Seccion hija = seccionRepository.save(new Seccion(documento, raiz, "Hija", "resumen hija"));

        Unidad u1 = unidadRepository.save(new Unidad(documento, hija, "U1", TipoContenido.DECLARATIVO, NivelImportancia.ESENCIAL));
        Unidad u2 = unidadRepository.save(new Unidad(documento, hija, "U2", TipoContenido.DECLARATIVO, NivelImportancia.ESENCIAL));
        Unidad u3 = unidadRepository.save(new Unidad(documento, hija, "U3", TipoContenido.DECLARATIVO, NivelImportancia.DETALLE));
        // No declarativa: no debe aparecer, no llega a jugarse nunca.
        unidadRepository.save(new Unidad(documento, hija, "U4", TipoContenido.PROCEDIMENTAL, NivelImportancia.ESENCIAL));

        MapaDocumentoResponse mapa = mapaDocumentoConsultaService.obtenerMapa(usuario, documento.getId());

        assertThat(mapa.secciones()).hasSize(2);
        SeccionMapaDTO raizDto = mapa.secciones().stream().filter(s -> s.id().equals(raiz.getId())).findFirst().orElseThrow();
        assertThat(raizDto.padreId()).isNull();
        assertThat(raizDto.unidades()).isEmpty();

        SeccionMapaDTO hijaDto = mapa.secciones().stream().filter(s -> s.id().equals(hija.getId())).findFirst().orElseThrow();
        assertThat(hijaDto.padreId()).isEqualTo(raiz.getId());
        assertThat(hijaDto.unidades())
                .extracting(UnidadMapaDTO::id, UnidadMapaDTO::nivelImportancia)
                .containsExactlyInAnyOrder(
                        tuple(u1.getId(), "esencial"), tuple(u2.getId(), "esencial"), tuple(u3.getId(), "detalle"));
    }

    @Test
    void rechazaDocumentosQueTodaviaNoTienenEstructuraMapeada() {
        inicializar(EstadoDocumento.PROCESANDO);

        assertThatThrownBy(() -> mapaDocumentoConsultaService.obtenerMapa(usuario, documento.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void rechazaSiElDocumentoNoPerteneceAlUsuario() {
        inicializar(EstadoDocumento.MAPEADO);
        Usuario otro = usuarioRepository.save(new Usuario("firebase-uid-mapa-otro-" + UUID.randomUUID(), "y@example.com"));

        assertThatThrownBy(() -> mapaDocumentoConsultaService.obtenerMapa(otro, documento.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void funcionaTambienConDocumentosYaListos() {
        inicializar(EstadoDocumento.LISTO);
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        Unidad u1 = unidadRepository.save(new Unidad(documento, seccion, "U1", TipoContenido.DECLARATIVO, NivelImportancia.IMPORTANTE));

        MapaDocumentoResponse mapa = mapaDocumentoConsultaService.obtenerMapa(usuario, documento.getId());

        assertThat(mapa.secciones()).singleElement().satisfies(
                s -> assertThat(s.unidades()).extracting(UnidadMapaDTO::id, UnidadMapaDTO::nivelImportancia)
                        .containsExactly(tuple(u1.getId(), "importante")));
    }
}
