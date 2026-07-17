package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.EstadoGeneracion;
import com.mentaro.backend.entity.NivelImportancia;
import com.mentaro.backend.entity.Seccion;
import com.mentaro.backend.entity.SecuenciaTablero;
import com.mentaro.backend.entity.TipoContenido;
import com.mentaro.backend.entity.TipoElemento;
import com.mentaro.backend.entity.Unidad;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.SeccionRepository;
import com.mentaro.backend.repository.SecuenciaTableroRepository;
import com.mentaro.backend.repository.UnidadRepository;
import com.mentaro.backend.repository.UsuarioRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SecuenciaTableroServiceTests {

    @Autowired
    private SecuenciaTableroService secuenciaTableroService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private DocumentoRepository documentoRepository;
    @Autowired
    private SeccionRepository seccionRepository;
    @Autowired
    private UnidadRepository unidadRepository;
    @Autowired
    private SecuenciaTableroRepository secuenciaTableroRepository;

    private Documento documento;
    private Seccion seccion;

    private void inicializar(EstadoDocumento estado) {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-seq-" + UUID.randomUUID(), "x@example.com"));
        documento = documentoRepository.save(new Documento(usuario, "Doc", estado));
        seccion = seccionRepository.save(new Seccion(documento, null, "Seccion", "resumen"));
    }

    private Unidad crearUnidadConContenido(String titulo, UUID... dependeDe) {
        Unidad unidad = new Unidad(documento, seccion, titulo, TipoContenido.DECLARATIVO, NivelImportancia.ESENCIAL);
        unidad.setDependeDe(dependeDe);
        unidad.asignarContenido("explicacion", "alternativa",
                "{\"enunciado\":\"e\",\"alternativas\":[\"a\",\"b\"],\"correcta_index\":0}",
                "{\"enunciado\":\"e\",\"alternativas\":[\"a\",\"b\"],\"correcta_index\":0}",
                EstadoGeneracion.GENERADA);
        return unidadRepository.save(unidad);
    }

    private List<SecuenciaTablero> secuenciaCompleta() {
        return secuenciaTableroRepository.findByDocumento_IdAndPosicionGreaterThanEqualOrderByPosicionAsc(
                documento.getId(), 0, Pageable.unpaged());
    }

    @Test
    void asignaPosicionesConHuecosDePaso100() {
        inicializar(EstadoDocumento.MAPEADO);
        Unidad a = crearUnidadConContenido("A");
        Unidad b = crearUnidadConContenido("B");
        Unidad c = crearUnidadConContenido("C");

        secuenciaTableroService.construir(documento, List.of(a, b, c));

        List<SecuenciaTablero> secuencia = secuenciaCompleta();
        assertThat(secuencia).hasSize(3);
        assertThat(secuencia).extracting(SecuenciaTablero::getPosicion).containsExactly(100, 200, 300);
        assertThat(secuencia).allSatisfy(e -> assertThat(e.getTipoElemento()).isEqualTo(TipoElemento.NUEVA));
    }

    @Test
    void respetaElOrdenDeDependencias() {
        inicializar(EstadoDocumento.MAPEADO);
        // Se crean deliberadamente en orden "incorrecto": C antes que B,
        // B antes que A, pero C depende de B y B depende de A.
        Unidad a = crearUnidadConContenido("A");
        Unidad b = crearUnidadConContenido("B", a.getId());
        Unidad c = crearUnidadConContenido("C", b.getId());

        // Se pasan en orden invertido a proposito: el servicio debe
        // reordenar igual, no confiar en el orden de la lista de entrada.
        secuenciaTableroService.construir(documento, List.of(c, b, a));

        List<SecuenciaTablero> secuencia = secuenciaCompleta();
        assertThat(secuencia).extracting(e -> e.getUnidad().getTitulo())
                .containsExactly("A", "B", "C");
    }

    @Test
    void ignoraDependenciasFueraDelConjuntoSeleccionado() {
        inicializar(EstadoDocumento.MAPEADO);
        UUID idFueraDelConjunto = UUID.randomUUID();
        Unidad a = crearUnidadConContenido("A", idFueraDelConjunto);

        secuenciaTableroService.construir(documento, List.of(a));

        assertThat(secuenciaCompleta()).hasSize(1);
    }

    @Test
    void ignoraUnidadesSinContenido() {
        inicializar(EstadoDocumento.MAPEADO);
        Unidad conContenido = crearUnidadConContenido("Con contenido");
        Unidad sinContenido = unidadRepository.save(
                new Unidad(documento, seccion, "Sin contenido", TipoContenido.DECLARATIVO, NivelImportancia.DETALLE));

        secuenciaTableroService.construir(documento, List.of(conContenido, sinContenido));

        assertThat(secuenciaCompleta()).hasSize(1);
    }

    @Test
    void alProfundizarAgregaDespuesDeLaUltimaPosicionExistente() {
        inicializar(EstadoDocumento.MAPEADO);
        Unidad a = crearUnidadConContenido("A");
        secuenciaTableroService.construir(documento, List.of(a));
        assertThat(secuenciaCompleta()).extracting(SecuenciaTablero::getPosicion).containsExactly(100);

        documento.setEstado(EstadoDocumento.LISTO);
        documentoRepository.save(documento);

        Unidad b = crearUnidadConContenido("B");
        secuenciaTableroService.construir(documento, List.of(b));

        List<SecuenciaTablero> secuencia = secuenciaCompleta();
        assertThat(secuencia).extracting(SecuenciaTablero::getPosicion).containsExactly(100, 200);
        assertThat(secuencia.get(0).getUnidad().getTitulo()).isEqualTo("A");
        assertThat(secuencia.get(1).getUnidad().getTitulo()).isEqualTo("B");
    }

    @Test
    void unCicloDeDependenciasNoBloqueaLaConstruccion() {
        inicializar(EstadoDocumento.MAPEADO);
        Unidad a = crearUnidadConContenido("A");
        Unidad b = crearUnidadConContenido("B", a.getId());
        // Crea el ciclo: A pasa a depender de B tambien.
        a.setDependeDe(new UUID[] {b.getId()});
        unidadRepository.save(a);

        secuenciaTableroService.construir(documento, List.of(a, b));

        // No debe lanzar excepcion ni perder unidades - ambas quedan en la
        // secuencia aunque el orden entre ellas no este garantizado.
        assertThat(secuenciaCompleta()).hasSize(2);
    }

    @Test
    void noHaceNadaSiNingunaUnidadTieneContenido() {
        inicializar(EstadoDocumento.MAPEADO);
        Unidad sinContenido = unidadRepository.save(
                new Unidad(documento, seccion, "Sin contenido", TipoContenido.DECLARATIVO, NivelImportancia.DETALLE));

        secuenciaTableroService.construir(documento, List.of(sinContenido));

        assertThat(secuenciaCompleta()).isEmpty();
    }
}
