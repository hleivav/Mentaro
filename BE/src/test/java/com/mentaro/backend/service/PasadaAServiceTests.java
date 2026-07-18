package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.mentaro.backend.deepseek.DeepSeekClient;
import com.mentaro.backend.deepseek.DeepSeekOpciones;
import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.Seccion;
import com.mentaro.backend.entity.Unidad;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.SeccionRepository;
import com.mentaro.backend.repository.UnidadRepository;
import com.mentaro.backend.repository.UsuarioRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PasadaAServiceTests {

    private static final String RESPUESTA_DEEPSEEK = """
            {
              "estructura": [
                {"id": "sec-1", "titulo": "Libro I", "padre_id": null, "resumen": "Introduccion"},
                {"id": "sec-2", "titulo": "Capitulo 1", "padre_id": "sec-1", "resumen": "El comienzo"}
              ],
              "unidades": [
                {"id": "u-1", "titulo": "Idea base", "seccion_id": "sec-2", "tipo_contenido": "declarativo", "nivel_importancia": "esencial", "depende_de": []},
                {"id": "u-2", "titulo": "Idea derivada", "seccion_id": "sec-2", "tipo_contenido": "declarativo", "nivel_importancia": "importante", "depende_de": ["u-1"]}
              ]
            }
            """;

    @Autowired
    private PasadaAService pasadaAService;
    @MockitoBean
    private DeepSeekClient deepSeekClient;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private DocumentoRepository documentoRepository;
    @Autowired
    private SeccionRepository seccionRepository;
    @Autowired
    private UnidadRepository unidadRepository;

    @Test
    void persisteEstructuraYEsqueletoDeUnidadesSinContenidoTodavia() {
        when(deepSeekClient.completar(any(DeepSeekOpciones.class), anyString(), anyString()))
                .thenReturn(RESPUESTA_DEEPSEEK);

        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-pasada-a", "pasadaA@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.PROCESANDO));

        pasadaAService.ejecutar(documento, "texto fuente del documento");

        List<Seccion> secciones = seccionRepository.findByDocumento_Id(documento.getId());
        assertThat(secciones).hasSize(2);
        Seccion raiz = secciones.stream().filter(s -> s.getPadre() == null).findFirst().orElseThrow();
        Seccion hija = secciones.stream().filter(s -> s.getPadre() != null).findFirst().orElseThrow();
        assertThat(raiz.getTitulo()).isEqualTo("Libro I");
        assertThat(hija.getTitulo()).isEqualTo("Capitulo 1");
        assertThat(hija.getPadre().getId()).isEqualTo(raiz.getId());

        List<Unidad> unidades = unidadRepository.findByDocumento_Id(documento.getId());
        assertThat(unidades).hasSize(2);
        assertThat(unidades).allSatisfy(u -> assertThat(u.tieneContenido()).isFalse());

        Unidad ideaBase = unidades.stream().filter(u -> u.getTitulo().equals("Idea base")).findFirst().orElseThrow();
        Unidad ideaDerivada = unidades.stream()
                .filter(u -> u.getTitulo().equals("Idea derivada")).findFirst().orElseThrow();
        assertThat(ideaDerivada.getDependeDe()).containsExactly(ideaBase.getId());
        assertThat(ideaBase.getSeccion().getId()).isEqualTo(hija.getId());

        Documento documentoActualizado = documentoRepository.findById(documento.getId()).orElseThrow();
        assertThat(documentoActualizado.getEstado()).isEqualTo(EstadoDocumento.MAPEADO);
    }

    @Test
    void descartaUnaUnidadConValorInvalidoSinTumbarElRestoDelMapeo() {
        // Reproduce el fallo real visto en produccion: DeepSeek devolvio
        // "detalle" (un valor valido de nivel_importancia) en el campo
        // tipo_contenido, que no tiene ese valor en su catalogo - antes esto
        // hacia fallar TODA la Pasada A (documento entero a ERROR, unidades
        // validas incluidas), pese a que "u-2" es perfectamente valida.
        String respuestaConValorInvalido = """
                {
                  "estructura": [
                    {"id": "sec-1", "titulo": "Libro I", "padre_id": null, "resumen": "Introduccion"}
                  ],
                  "unidades": [
                    {"id": "u-1", "titulo": "Unidad rota", "seccion_id": "sec-1", "tipo_contenido": "detalle", "nivel_importancia": "esencial", "depende_de": []},
                    {"id": "u-2", "titulo": "Unidad valida", "seccion_id": "sec-1", "tipo_contenido": "declarativo", "nivel_importancia": "esencial", "depende_de": ["u-1"]}
                  ]
                }
                """;
        when(deepSeekClient.completar(any(DeepSeekOpciones.class), anyString(), anyString()))
                .thenReturn(respuestaConValorInvalido);

        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-pasada-a-invalida", "invalida@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.PROCESANDO));

        pasadaAService.ejecutar(documento, "texto fuente del documento");

        List<Unidad> unidades = unidadRepository.findByDocumento_Id(documento.getId());
        assertThat(unidades).singleElement().satisfies(u -> {
            assertThat(u.getTitulo()).isEqualTo("Unidad valida");
            // La dependencia hacia la unidad descartada tambien se descarta
            // en silencio, no rompe el guardado de la unidad valida.
            assertThat(u.getDependeDe()).isEmpty();
        });

        Documento documentoActualizado = documentoRepository.findById(documento.getId()).orElseThrow();
        assertThat(documentoActualizado.getEstado()).isEqualTo(EstadoDocumento.MAPEADO);
    }

    @Test
    void rechazaDocumentosQueNoEstanEnProcesando() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-pasada-a-2", "otro@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.LISTO));

        assertThrows(IllegalStateException.class, () -> pasadaAService.ejecutar(documento, "texto"));
    }
}
