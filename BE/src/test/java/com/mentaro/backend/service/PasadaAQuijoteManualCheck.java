package com.mentaro.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentaro.backend.entity.Documento;
import com.mentaro.backend.entity.EstadoDocumento;
import com.mentaro.backend.entity.Seccion;
import com.mentaro.backend.entity.Unidad;
import com.mentaro.backend.entity.Usuario;
import com.mentaro.backend.repository.DocumentoRepository;
import com.mentaro.backend.repository.SeccionRepository;
import com.mentaro.backend.repository.UnidadRepository;
import com.mentaro.backend.repository.UsuarioRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

// Chequeo MANUAL, no parte de la suite normal. Pega contra la API real de
// DeepSeek - cuesta dinero y tokens. Dos capas de proteccion contra correrlo
// sin querer: (1) el nombre de la clase no termina en Test/Tests, asi que
// `mvn test` no lo recoge solo; (2) @EnabledIfSystemProperty se salta el
// test aunque lo apunten explicito con -Dtest, a menos que se pase tambien
// la property de abajo - asi un -Dtest mal armado (ej. un patron de
// exclusion que Surefire interprete distinto a lo esperado) no dispara una
// corrida real por accidente. Correr a mano con:
//   mvn test -Dtest=PasadaAQuijoteManualCheck -Ddeepseek.live=true
// Requiere DEEPSEEK_API_KEY real en BE/.env.
@SpringBootTest
@Transactional
@EnabledIfSystemProperty(named = "deepseek.live", matches = "true")
class PasadaAQuijoteManualCheck {

    @Autowired
    private PasadaAService pasadaAService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private DocumentoRepository documentoRepository;
    @Autowired
    private SeccionRepository seccionRepository;
    @Autowired
    private UnidadRepository unidadRepository;

    @Test
    void mapeaLosPrimerosTresCapitulosDelQuijote() throws IOException {
        String texto = Files.readString(Path.of("src/test/resources/quijote-caps-1-3.txt"));

        Usuario usuario = usuarioRepository.save(new Usuario("firebase-uid-quijote-check", "check@example.com"));
        Documento documento = documentoRepository
                .save(new Documento(usuario, "Don Quijote (caps. 1-3)", EstadoDocumento.PROCESANDO));

        pasadaAService.ejecutar(documento, texto);

        List<Seccion> secciones = seccionRepository.findByDocumento_Id(documento.getId());
        List<Unidad> unidades = unidadRepository.findByDocumento_Id(documento.getId());

        System.out.println("=== ESTRUCTURA (" + secciones.size() + " secciones) ===");
        secciones.forEach(s -> System.out.println(
                (s.getPadre() == null ? "- " : "  - ") + s.getTitulo() + " :: " + s.getResumen()));

        System.out.println("\n=== UNIDADES (" + unidades.size() + ") ===");
        unidades.forEach(u -> System.out.println(
                "- [" + u.getNivelImportancia() + "/" + u.getTipoContenido() + "] " + u.getTitulo()
                        + " (seccion: " + u.getSeccion().getTitulo() + ", depende_de: " + u.getDependeDe().length + ")"));

        Map<Object, Long> porNivel = unidades.stream()
                .collect(Collectors.groupingBy(Unidad::getNivelImportancia, Collectors.counting()));
        System.out.println("\n=== CONTEO POR NIVEL_IMPORTANCIA ===");
        porNivel.forEach((nivel, cuenta) -> System.out.println(nivel + ": " + cuenta));

        Documento documentoFinal = documentoRepository.findById(documento.getId()).orElseThrow();
        System.out.println("\n=== ESTADO FINAL: " + documentoFinal.getEstado() + " ===");

        assertThat(secciones).isNotEmpty();
        assertThat(unidades).isNotEmpty();
        assertThat(unidades).allSatisfy(u -> assertThat(u.tieneContenido()).isFalse());
        assertThat(documentoFinal.getEstado()).isEqualTo(EstadoDocumento.MAPEADO);
    }
}
