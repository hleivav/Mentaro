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
import com.mentaro.backend.entity.SecuenciaTablero;
import com.mentaro.backend.entity.TipoContenido;
import com.mentaro.backend.entity.TipoElemento;
import com.mentaro.backend.entity.Unidad;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.DocumentoImagenTemporalRepository;
import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.DocumentoTextoTemporalRepository;
import com.mentaro.backend.repository.ProgresoUsuarioRepository;
import com.mentaro.backend.repository.ResultadoUnidadRepository;
import com.mentaro.backend.repository.SeccionRepository;
import com.mentaro.backend.repository.SecuenciaTableroRepository;
import com.mentaro.backend.repository.UnidadRepository;
import com.mentaro.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class DocumentoEliminacionServiceTests {

    @Autowired
    private DocumentoEliminacionService documentoEliminacionService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private DocumentoTextoTemporalService textoTemporalService;
    @Autowired
    private DocumentoImagenTemporalService imagenTemporalService;
    @Autowired
    private DocumentoImagenTemporalRepository documentoImagenTemporalRepository;
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
    @Autowired
    private ProgresoUsuarioRepository progresoUsuarioRepository;
    @Autowired
    private ResultadoUnidadRepository resultadoUnidadRepository;
    @Autowired
    private DocumentoTextoTemporalRepository documentoTextoTemporalRepository;

    @Test
    void borraElDocumentoYTodoLoQueDependeDeEl() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-elim-" + UUID.randomUUID(), "x@example.com"));
        Documento documento = documentoRepository.save(new Documento(usuario, "Doc", EstadoDocumento.LISTO));

        // Secciones anidadas (auto-referenciadas via padre_id) - el caso que
        // podria romper un borrado mal ordenado.
        Seccion raiz = seccionRepository.save(new Seccion(documento, null, "Raiz", "resumen"));
        Seccion hija = seccionRepository.save(new Seccion(documento, raiz, "Hija", "resumen"));

        Unidad unidad = new Unidad(documento, hija, "U1", TipoContenido.DECLARATIVO, NivelImportancia.ESENCIAL);
        unidad.asignarContenido("corta", "alt", "{\"enunciado\":\"e\",\"alternativas\":[\"a\"],\"correcta_index\":0}",
                "{\"enunciado\":\"e\",\"alternativas\":[\"a\"],\"correcta_index\":0}", EstadoGeneracion.GENERADA);
        unidad = unidadRepository.save(unidad);

        secuenciaTableroRepository.save(new SecuenciaTablero(documento, 100, unidad, TipoElemento.NUEVA));
        ProgresoUsuario progreso = progresoUsuarioRepository.save(new ProgresoUsuario(usuario, documento));
        progreso.setPosicionActual(100);
        progresoUsuarioRepository.save(progreso);
        resultadoUnidadRepository.save(new ResultadoUnidad(usuario, unidad, EstadoResultado.DOMINADA));
        textoTemporalService.guardar(documento.getId(), "texto de prueba");
        imagenTemporalService.guardar(documento.getId(),
                java.util.List.of(new DescriptorImagenesPdf.ImagenDescrita(0, "desc", new byte[] {1})));

        UUID documentoId = documento.getId();
        UUID unidadId = unidad.getId();

        documentoEliminacionService.eliminar(usuario, documentoId);
        // documentoRepository.delete(...) es un borrado JPA normal (no bulk
        // como los demas) - Hibernate lo deja pendiente en la cola de
        // acciones hasta el proximo flush, no lo ejecuta al toque. Sin
        // flush() antes de clear(), esa eliminacion pendiente se pierde en
        // vez de llegar a la base de datos. Y documento_texto_temporal se
        // borra por ON DELETE CASCADE a nivel de base de datos (no hay
        // relacion mapeada en JPA), asi que el contexto de persistencia
        // tampoco se entera solo - clear() fuerza que el siguiente findById
        // pegue contra la DB de verdad en vez de la instancia cacheada.
        entityManager.flush();
        entityManager.clear();

        assertThat(documentoRepository.findById(documentoId)).isEmpty();
        assertThat(seccionRepository.findByDocumento_Id(documentoId)).isEmpty();
        assertThat(unidadRepository.findByDocumento_Id(documentoId)).isEmpty();
        assertThat(secuenciaTableroRepository
                .findByDocumento_IdAndPosicionGreaterThanEqualOrderByPosicionAsc(
                        documentoId, 0, org.springframework.data.domain.Pageable.unpaged()))
                .isEmpty();
        assertThat(progresoUsuarioRepository.findByUsuario_IdAndDocumento_Id(usuario.getId(), documentoId)).isEmpty();
        assertThat(resultadoUnidadRepository.findByUsuario_IdAndUnidad_Id(usuario.getId(), unidadId)).isEmpty();
        // documento_texto_temporal tiene ON DELETE CASCADE - debe irse solo.
        assertThat(documentoTextoTemporalRepository.findById(documentoId)).isEmpty();
        // documento_imagen_temporal tambien tiene ON DELETE CASCADE.
        assertThat(documentoImagenTemporalRepository.findByDocumentoIdOrderByPaginaAscOrdenAsc(documentoId)).isEmpty();
    }

    @Test
    void lanza403SiElDocumentoNoPerteneceAlUsuario() {
        Usuario dueno = usuarioRepository.save(new Usuario("firebase-uid-elim-dueno-" + UUID.randomUUID(), "d@example.com"));
        Usuario otro = usuarioRepository.save(new Usuario("firebase-uid-elim-otro-" + UUID.randomUUID(), "o@example.com"));
        Documento documento = documentoRepository.save(new Documento(dueno, "Doc", EstadoDocumento.LISTO));

        assertThatThrownBy(() -> documentoEliminacionService.eliminar(otro, documento.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");

        assertThat(documentoRepository.findById(documento.getId())).isPresent();
    }

    @Test
    void lanza404SiElDocumentoNoExiste() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-elim-404-" + UUID.randomUUID(), "y@example.com"));

        assertThatThrownBy(() -> documentoEliminacionService.eliminar(usuario, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
