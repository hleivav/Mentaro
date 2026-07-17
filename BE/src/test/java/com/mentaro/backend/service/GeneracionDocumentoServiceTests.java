package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class GeneracionDocumentoServiceTests {

    @Autowired
    private GeneracionDocumentoService generacionDocumentoService;
    @Autowired
    private DocumentoTextoTemporalService textoTemporalService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private DocumentoRepository documentoRepository;
    @Autowired
    private SeccionRepository seccionRepository;
    @Autowired
    private UnidadRepository unidadRepository;
    @MockitoBean
    private PasadaBAsyncRunner pasadaBAsyncRunner;

    private Usuario usuario;
    private Documento documento;
    private Unidad unidad;

    private void inicializar(EstadoDocumento estado) {
        usuario = usuarioRepository.save(new Usuario("firebase-uid-gen-" + UUID.randomUUID(), "x@example.com"));
        documento = documentoRepository.save(new Documento(usuario, "Doc", estado));
        Seccion seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
        unidad = unidadRepository.save(
                new Unidad(documento, seccion, "Titulo", TipoContenido.DECLARATIVO, NivelImportancia.ESENCIAL));
    }

    @Test
    void primeraGeneracionFijaGenerandoDeInmediatoYDisparaElAsync() {
        inicializar(EstadoDocumento.MAPEADO);
        textoTemporalService.guardar(documento.getId(), "texto fuente");

        Documento resultado = generacionDocumentoService.generar(usuario, documento.getId(), List.of(unidad.getId()));

        assertThat(resultado.getEstado()).isEqualTo(EstadoDocumento.GENERANDO);
        assertThat(documentoRepository.findById(documento.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoDocumento.GENERANDO);
        verify(pasadaBAsyncRunner).ejecutar(eq(documento.getId()), eq(List.of(unidad.getId())), eq("texto fuente"));
    }

    @Test
    void profundizarNoTocaElEstadoListo() {
        inicializar(EstadoDocumento.LISTO);
        textoTemporalService.guardar(documento.getId(), "texto fuente");

        Documento resultado = generacionDocumentoService.generar(usuario, documento.getId(), List.of(unidad.getId()));

        assertThat(resultado.getEstado()).isEqualTo(EstadoDocumento.LISTO);
        verify(pasadaBAsyncRunner).ejecutar(eq(documento.getId()), eq(List.of(unidad.getId())), eq("texto fuente"));
    }

    @Test
    void rechazaDocumentosQueNoEstanMapeadosNiListos() {
        inicializar(EstadoDocumento.PROCESANDO);
        textoTemporalService.guardar(documento.getId(), "texto fuente");

        assertThatThrownBy(() -> generacionDocumentoService.generar(usuario, documento.getId(), List.of(unidad.getId())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        verifyNoInteractions(pasadaBAsyncRunner);
    }

    @Test
    void propagaElA410SiElTextoFuenteYaExpiroEnVezDeReintentarSilenciosamente() {
        inicializar(EstadoDocumento.MAPEADO);
        // No se guarda texto temporal - simula que ya fue barrido por
        // inactividad (ver LimpiezaTextoTemporalJob). El backend no puede
        // "reintentar la Pasada A" solo, porque nunca se guarda el archivo
        // original (principio de copyright) - la senal clara (410) es el
        // cierre correcto del contrato, no un reintento silencioso.
        assertThatThrownBy(() -> generacionDocumentoService.generar(usuario, documento.getId(), List.of(unidad.getId())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("410");
        verifyNoInteractions(pasadaBAsyncRunner);
        assertThat(documentoRepository.findById(documento.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoDocumento.MAPEADO);
    }

    @Test
    void rechazaSiElDocumentoNoPerteneceAlUsuario() {
        inicializar(EstadoDocumento.MAPEADO);
        textoTemporalService.guardar(documento.getId(), "texto fuente");
        Usuario otro = usuarioRepository.save(new Usuario("firebase-uid-gen-otro-" + UUID.randomUUID(), "y@example.com"));

        assertThatThrownBy(() -> generacionDocumentoService.generar(otro, documento.getId(), List.of(unidad.getId())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verifyNoInteractions(pasadaBAsyncRunner);
    }
}
