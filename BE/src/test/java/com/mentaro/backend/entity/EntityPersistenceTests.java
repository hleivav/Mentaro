package com.mentaro.backend.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.ProgresoUsuarioRepository;
import com.mentaro.backend.repository.ResultadoUnidadRepository;
import com.mentaro.backend.repository.SeccionRepository;
import com.mentaro.backend.repository.SecuenciaTableroRepository;
import com.mentaro.backend.repository.UnidadRepository;
import com.mentaro.backend.repository.UsuarioRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class EntityPersistenceTests {

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

    @Test
    void persistsAndReloadsFullChain() {
        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-1", "test@example.com"));

        Documento documento = documentoRepository
                .save(new Documento(usuario, "El Quijote", EstadoDocumento.PROCESANDO));
        documento.setEstado(EstadoDocumento.LISTO);
        documento = documentoRepository.save(documento);

        Seccion seccion = seccionRepository.save(
                new Seccion(documento, null, "Libro I", "Introduccion al metodo socratico"));

        Unidad unidad = unidadRepository.save(new Unidad(
                documento, seccion, "El método socrático", TipoContenido.DECLARATIVO, NivelImportancia.ESENCIAL));
        assertThat(unidad.tieneContenido()).isFalse();

        unidad.asignarContenido(
                "Sócrates no daba respuestas, hacía preguntas...",
                "Explicación alternativa distinta a la primera.",
                "{\"enunciado\": \"¿Qué es el método socrático?\", \"alternativas\": [\"a\", \"b\"]}",
                "{\"enunciado\": \"¿Quién lo practicaba?\", \"alternativas\": [\"a\", \"b\"]}",
                EstadoGeneracion.GENERADA);
        unidad.setDependeDe(new UUID[] {UUID.randomUUID(), UUID.randomUUID()});
        unidad = unidadRepository.save(unidad);
        assertThat(unidad.tieneContenido()).isTrue();

        secuenciaTableroRepository.save(new SecuenciaTablero(documento, 0, unidad, TipoElemento.NUEVA));

        ProgresoUsuario progreso = progresoUsuarioRepository.save(new ProgresoUsuario(usuario, documento));

        ResultadoUnidad resultado = resultadoUnidadRepository
                .save(new ResultadoUnidad(usuario, unidad, EstadoResultado.VISTA));
        resultado.setEstado(EstadoResultado.PENDIENTE_REFUERZO);
        resultado = resultadoUnidadRepository.save(resultado);

        assertThat(usuarioRepository.findByFirebaseUid("firebase-uid-1")).contains(usuario);

        assertThat(documentoRepository.findByUsuario_IdOrderByCreadoEnDesc(usuario.getId()))
                .extracting(Documento::getEstado)
                .containsExactly(EstadoDocumento.LISTO);

        assertThat(unidadRepository.findByDocumento_Id(documento.getId()))
                .singleElement()
                .satisfies(u -> assertThat(u.getDependeDe()).hasSize(2));

        List<SecuenciaTablero> elementos = secuenciaTableroRepository
                .findByDocumento_IdAndPosicionGreaterThanEqualOrderByPosicionAsc(
                        documento.getId(), 0, PageRequest.of(0, 8));
        assertThat(elementos).singleElement()
                .satisfies(e -> assertThat(e.getTipoElemento()).isEqualTo(TipoElemento.NUEVA));

        assertThat(progresoUsuarioRepository.findByUsuario_IdAndDocumento_Id(usuario.getId(), documento.getId()))
                .contains(progreso);

        assertThat(resultadoUnidadRepository.findByUsuario_IdAndUnidad_Id(usuario.getId(), unidad.getId()))
                .get()
                .extracting(ResultadoUnidad::getEstado)
                .isEqualTo(EstadoResultado.PENDIENTE_REFUERZO);
    }
}
